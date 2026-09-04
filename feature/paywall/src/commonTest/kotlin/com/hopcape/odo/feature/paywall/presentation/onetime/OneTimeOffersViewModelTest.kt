package com.hopcape.odo.feature.paywall.presentation.onetime

import arrow.core.Either
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.OneTimeProducts
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OneTimeOffersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val tracked = RecordingTracker()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

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
        val viewModel = viewModel(ThrowingPurchaser)
        advanceUntilIdle()

        assertIs<Loadable.Failed>(viewModel.state.value.offers)
        assertTrue(tracked.names.contains(PaywallTelemetry.Event.ONE_TIME_UNAVAILABLE))
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

    /**
     * Nothing is bought yet — the purchase path and the balances it would credit are the next
     * slice. A tap is the only signal there is that anyone wants one of these, so it is
     * reported and nothing else happens.
     */
    @Test
    fun tappingAnOffer_reportsItAndBuysNothing() = runTest(dispatcher) {
        val purchaser = FakePurchaser(ALL_PRICED)
        val viewModel = viewModel(purchaser)
        advanceUntilIdle()

        viewModel.onEvent(OneTimeOffersEvent.OfferTapped(OneTimeProducts.BILL_CHECK_PACK))
        advanceUntilIdle()

        assertEquals(
            OneTimeProducts.BILL_CHECK_PACK,
            tracked.propertyOf(PaywallTelemetry.Event.ONE_TIME_TAPPED, PaywallTelemetry.Key.PRODUCT),
        )
        assertTrue(purchaser.purchased.isEmpty(), "nothing may be charged for until credits exist")
    }

    @Test
    fun closing_dismissesTheSheet() = runTest(dispatcher) {
        val viewModel = viewModel(FakePurchaser(ALL_PRICED))
        advanceUntilIdle()

        viewModel.onEvent(OneTimeOffersEvent.CloseTapped)

        assertEquals(OneTimeOffersEffect.Dismiss, viewModel.effects.first())
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun viewModel(purchaser: OneTimePurchaser) = OneTimeOffersViewModel(
        purchaser = purchaser,
        telemetry = PaywallTelemetry(logger = NoopLogger, analytics = tracked, ids = { "id" }),
    )

    private open class FakePurchaser(private val prices: Map<String, String>) : OneTimePurchaser {
        val purchased = mutableListOf<String>()

        override suspend fun purchase(productId: String): Either<DomainError, Unit> {
            purchased += productId
            return Unit.right()
        }

        override suspend fun priceOf(productId: String): String? = prices[productId]
    }

    private class FlakyPurchaser(prices: Map<String, String>) : FakePurchaser(prices) {
        var failing = true
        override suspend fun priceOf(productId: String): String? =
            if (failing) error("store unreachable") else super.priceOf(productId)
    }

    private object ThrowingPurchaser : OneTimePurchaser {
        override suspend fun purchase(productId: String): Either<DomainError, Unit> =
            error("not called")

        override suspend fun priceOf(productId: String): String? = error("store unreachable")
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
