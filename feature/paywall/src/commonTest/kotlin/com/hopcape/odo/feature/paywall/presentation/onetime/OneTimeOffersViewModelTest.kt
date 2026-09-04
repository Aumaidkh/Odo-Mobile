package com.hopcape.odo.feature.paywall.presentation.onetime

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.CompletedPurchase
import com.hopcape.odo.core.domain.subscription.OneTimeProducts
import com.hopcape.odo.core.domain.subscription.PurchaseReconciler
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import com.hopcape.odo.feature.paywall.presentation.PaywallTelemetry
import com.hopcape.odo.feature.paywall.presentation.state.Loadable
import com.hopcape.odo.feature.paywall.presentation.state.valueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OneTimeOffersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val tracked = RecordingTracker()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The context decides what is worth showing. Someone who ran out of bill checks is not
     * shopping for a PDF, and listing one is noise on the screen where they are deciding.
     */
    @Test
    fun aContextShowsOnlyItsOwnProducts_andPutsOneForward() = runTest(dispatcher) {
        val viewModel = viewModel(FakePurchaser(ALL_PRICED), OneTimeContext.BILL_CHECK)
        advanceUntilIdle()

        val cards = viewModel.state.value.offers.valueOrNull.orEmpty()

        assertEquals(
            listOf(OneTimeOffer.BILL_CHECK_PACK, OneTimeOffer.BILL_CHECK_SINGLE),
            cards.map { it.offer },
        )
        assertEquals(listOf(true, false), cards.map { it.recommended })
    }

    /** One product leaves nothing to recommend over anything. */
    @Test
    fun aSingleProductContextRecommendsNothing() = runTest(dispatcher) {
        val viewModel = viewModel(FakePurchaser(ALL_PRICED), OneTimeContext.EXPORT)
        advanceUntilIdle()

        val cards = viewModel.state.value.offers.valueOrNull.orEmpty()

        assertEquals(listOf(OneTimeOffer.RECORD_EXPORT), cards.map { it.offer })
        assertTrue(cards.none { it.recommended })
    }

    /** A key written by an older build, or a bad deep link, sells the same things. */
    @Test
    fun anUnknownContextFallsBackToTheGenericOne() {
        assertEquals(OneTimeContext.GENERIC, OneTimeContext.of("NOT_A_CONTEXT"))
        assertEquals(OneTimeContext.BILL_CHECK, OneTimeContext.of("BILL_CHECK"))
    }

    @Test
    fun everyPricedProductIsListed_inTheDeclaredOrder() = runTest(dispatcher) {
        val viewModel = viewModel(FakePurchaser(ALL_PRICED))
        advanceUntilIdle()

        val cards = viewModel.state.value.offers.valueOrNull.orEmpty()

        assertEquals(OneTimeOffer.entries, cards.map { it.offer })
        assertEquals(listOf("₹49", "₹99", "₹99"), cards.map { it.price })
    }

    /**
     * The store's string is shown as-is. Nothing here formats money, so a price change in
     * Play Console reaches the sheet with no release.
     */
    @Test
    fun thePriceIsWhateverTheStoreSaid() = runTest(dispatcher) {
        val viewModel = viewModel(FakePurchaser(mapOf(OneTimeProducts.RECORD_EXPORT to "US$1.99")))
        advanceUntilIdle()

        assertEquals("US$1.99", viewModel.state.value.offers.valueOrNull?.single()?.price)
    }

    /**
     * A product nobody has created in the store has no price, and a row with no price is
     * either a lie or a dead end. It is left out rather than rendered.
     */
    @Test
    fun aProductTheStoreDoesNotKnowIsLeftOut() = runTest(dispatcher) {
        val viewModel = viewModel(FakePurchaser(mapOf(OneTimeProducts.BILL_CHECK_SINGLE to "₹49")))
        advanceUntilIdle()

        val cards = viewModel.state.value.offers.valueOrNull.orEmpty()

        assertEquals(listOf(OneTimeOffer.BILL_CHECK_SINGLE), cards.map { it.offer })
    }

    /** Today's real case: none of the three exist yet, so the sheet has nothing to sell. */
    @Test
    fun noProductsAtAll_readsAsEmptyRatherThanFailed() = runTest(dispatcher) {
        val viewModel = viewModel(FakePurchaser(emptyMap()))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isEmpty)
        assertIs<Loadable.Ready<*>>(viewModel.state.value.offers)
        assertEquals(0, tracked.propertyOf(PaywallTelemetry.Event.ONE_TIME_SHOWN, PaywallTelemetry.Key.COUNT))
    }

    /**
     * A store that cannot be reached is not an empty catalogue. Showing two of three would
     * leave the owner choosing from a list that is short for a reason nobody told them.
     */
    @Test
    fun aStoreThatCannotBeReached_failsTheWholeSheet() = runTest(dispatcher) {
        val viewModel = viewModel(UnreachablePurchaser)
        advanceUntilIdle()

        assertIs<Loadable.Failed>(viewModel.state.value.offers)
        assertTrue(tracked.names.contains(PaywallTelemetry.Event.ONE_TIME_UNAVAILABLE))
    }

    /** A purchaser that throws is still a failure, not an empty catalogue. */
    @Test
    fun aPurchaserThatThrows_alsoFailsTheSheet() = runTest(dispatcher) {
        val viewModel = viewModel(ThrowingPurchaser)
        advanceUntilIdle()

        assertIs<Loadable.Failed>(viewModel.state.value.offers)
    }

    /**
     * One opening, one report. A retry is the same sheet, and counting it twice makes "how
     * many owners were shown nothing" a number nobody can read.
     */
    @Test
    fun retryingDoesNotReportTheSheetTwice() = runTest(dispatcher) {
        val purchaser = FlakyPurchaser(ALL_PRICED)
        val viewModel = viewModel(purchaser)
        advanceUntilIdle()

        purchaser.failing = false
        viewModel.onEvent(OneTimeOffersEvent.RetryTapped)
        advanceUntilIdle()

        assertEquals(1, tracked.names.count { it == PaywallTelemetry.Event.ONE_TIME_SHOWN })
    }

    @Test
    fun retryAfterAFailure_canSucceed() = runTest(dispatcher) {
        val purchaser = FlakyPurchaser(ALL_PRICED)
        val viewModel = viewModel(purchaser)
        advanceUntilIdle()
        assertIs<Loadable.Failed>(viewModel.state.value.offers)

        purchaser.failing = false
        viewModel.onEvent(OneTimeOffersEvent.RetryTapped)
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.offers.valueOrNull?.size)
    }

    /** Bought, then claimed — the claim is what turns a payment into a balance. */
    @Test
    fun buyingAPack_creditsWhatItSold() = runTest(dispatcher) {
        val purchaser = FakePurchaser(ALL_PRICED)
        val viewModel = viewModel(purchaser)
        advanceUntilIdle()

        viewModel.onEvent(OneTimeOffersEvent.OfferTapped(OneTimeProducts.BILL_CHECK_PACK))
        advanceUntilIdle()

        assertEquals(listOf(OneTimeProducts.BILL_CHECK_PACK), purchaser.purchased)
        assertEquals(1, reconciler.claims, "the reconciler is what credits it")
        assertTrue(tracked.names.contains(PaywallTelemetry.Event.ONE_TIME_PURCHASED))
    }

    /** Every product on the sheet is sellable now that each one has a grant behind it. */
    @Test
    fun everyOfferOnTheSheetCanBeBought() = runTest(dispatcher) {
        val purchaser = FakePurchaser(ALL_PRICED)
        val viewModel = viewModel(purchaser)
        advanceUntilIdle()

        OneTimeOffer.entries.forEach { offer ->
            viewModel.onEvent(OneTimeOffersEvent.OfferTapped(offer.productId))
            advanceUntilIdle()
        }

        assertEquals(OneTimeOffer.entries.map { it.productId }, purchaser.purchased)
        assertNull(viewModel.state.value.notice)
    }

    /** Backing out is a decision, not a fault. It must not put an error on screen. */
    @Test
    fun backingOutOfTheStore_saysNothingAndCreditsNothing() = runTest(dispatcher) {
        val viewModel = viewModel(RefusingPurchaser(DomainError.PaymentCancelled))
        advanceUntilIdle()

        viewModel.onEvent(OneTimeOffersEvent.OfferTapped(OneTimeProducts.BILL_CHECK_PACK))
        advanceUntilIdle()

        assertNull(viewModel.state.value.notice)
        assertEquals(0, reconciler.claims, "nothing was paid for, so nothing may be claimed")
        assertTrue(tracked.names.contains(PaywallTelemetry.Event.ONE_TIME_CANCELLED))
    }

    @Test
    fun aRefusedPayment_saysSoAndCreditsNothing() = runTest(dispatcher) {
        val viewModel = viewModel(RefusingPurchaser(DomainError.PaymentFailed))
        advanceUntilIdle()

        viewModel.onEvent(OneTimeOffersEvent.OfferTapped(OneTimeProducts.BILL_CHECK_PACK))
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.notice)
        assertEquals(0, reconciler.claims)
        assertTrue(tracked.names.contains(PaywallTelemetry.Event.ONE_TIME_FAILED))
    }

    @Test
    fun closing_dismissesTheSheet() = runTest(dispatcher) {
        val viewModel = viewModel(FakePurchaser(ALL_PRICED))
        advanceUntilIdle()

        viewModel.onEvent(OneTimeOffersEvent.CloseTapped)

        assertEquals(OneTimeOffersEffect.Dismiss, viewModel.effects.first())
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private val reconciler = RecordingReconciler()

    private fun viewModel(
        purchaser: OneTimePurchaser,
        context: OneTimeContext = OneTimeContext.GENERIC,
    ) = OneTimeOffersViewModel(
        context = context,
        purchaser = purchaser,
        reconciler = reconciler,
        telemetry = PaywallTelemetry(logger = NoopLogger, analytics = tracked, ids = { "id" }),
    )

    /**
     * The sheet does not credit anything itself, so what these tests can check is that it
     * asks the one thing that does. What a claim awards is covered where the reconciler is.
     */
    private class RecordingReconciler : PurchaseReconciler {
        var claims = 0
            private set

        override suspend fun claimOutstanding() { claims++ }
    }

    private open class FakePurchaser(private val prices: Map<String, String>) : OneTimePurchaser {
        val purchased = mutableListOf<String>()

        override suspend fun purchase(productId: String): Either<DomainError, Unit> {
            purchased += productId
            return Unit.right()
        }

        override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
            prices.filterKeys { it in productIds }.right()

        /** One transaction per purchase, so claiming can tell two packs apart. */
        override suspend fun completedPurchases(): Either<DomainError, List<CompletedPurchase>> =
            purchased.mapIndexed { index, id -> CompletedPurchase("txn-$index", id) }.right()
    }

    private class FlakyPurchaser(prices: Map<String, String>) : FakePurchaser(prices) {
        var failing = true
        override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
            if (failing) DomainError.StoreUnavailable.left() else super.pricesOf(productIds)
    }

    /** The shape the real adapter reports: the store itself could not be asked. */
    private object UnreachablePurchaser : OneTimePurchaser {
        override suspend fun purchase(productId: String): Either<DomainError, Unit> =
            error("not called")

        override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
            DomainError.StoreUnavailable.left()

        override suspend fun completedPurchases(): Either<DomainError, List<CompletedPurchase>> =
            DomainError.StoreUnavailable.left()
    }

    /** A purchaser that throws outright — the unconfigured-SDK case, not a store error. */
    private class RefusingPurchaser(private val error: DomainError) : OneTimePurchaser {
        override suspend fun purchase(productId: String): Either<DomainError, Unit> = error.left()

        override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
            ALL_PRICED.filterKeys { it in productIds }.right()

        override suspend fun completedPurchases(): Either<DomainError, List<CompletedPurchase>> =
            emptyList<CompletedPurchase>().right()
    }

    private object ThrowingPurchaser : OneTimePurchaser {
        override suspend fun purchase(productId: String): Either<DomainError, Unit> =
            error("not called")

        override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
            error("RevenueCat is not configured")

        override suspend fun completedPurchases(): Either<DomainError, List<CompletedPurchase>> =
            error("RevenueCat is not configured")
    }

    private class RecordingTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()

        val names: List<String> get() = events.map { it.first }

        fun propertyOf(event: String, key: String): Any? =
            events.firstOrNull { it.first == event }?.second?.get(key)

        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) {
            events += eventName to properties
        }

        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit

        override fun flush() = Unit
    }

    private companion object {
        val ALL_PRICED = mapOf(
            OneTimeProducts.BILL_CHECK_SINGLE to "₹49",
            OneTimeProducts.BILL_CHECK_PACK to "₹99",
            OneTimeProducts.RECORD_EXPORT to "₹99",
        )
    }
}
