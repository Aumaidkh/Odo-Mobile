package com.hopcape.odo.feature.paywall.presentation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.BillingPeriod
import com.hopcape.odo.core.domain.subscription.Offer
import com.hopcape.odo.core.domain.subscription.PlanOption
import com.hopcape.odo.core.domain.subscription.RestoreOutcome
import com.hopcape.odo.core.domain.subscription.SubscriptionCatalog
import com.hopcape.odo.core.domain.subscription.SubscriptionPurchaser
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /* ------------------------------ loading the offer ------------------------------ */

    @Test
    fun theOfferIsReadWhenTheScreenOpens() = runTest {
        val viewModel = viewModel()

        advanceUntilIdle()

        val offer = viewModel.state.value.offer.valueOrNull
        assertEquals(listOf(MONTHLY_ID, ANNUAL_ID), offer?.plans?.map { it.id })
    }

    @Test
    fun everyPriceOnScreenIsTheStoresOwnString() = runTest {
        val viewModel = viewModel()

        advanceUntilIdle()

        val plans = viewModel.state.value.offer.valueOrNull?.plans.orEmpty()
        assertEquals("₹149.00", plans.first().price)
        assertEquals("₹1,490.00", plans.last().price)
        assertEquals("₹124.17", plans.last().pricePerMonth, "the per-month figure is the store's too")
    }

    @Test
    fun theAnnualPlanOpensSelected() = runTest {
        // It is the better deal and the one the saving badge is about, so the screen should
        // not open arguing with itself.
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(ANNUAL_ID, viewModel.state.value.offer.valueOrNull?.selectedPlanId)
    }

    @Test
    fun aStoreThatCannotBeReachedShowsNoPriceAtAll() = runTest {
        val viewModel = viewModel(catalog = { DomainError.StoreUnavailable.left() })

        advanceUntilIdle()

        assertIs<Loadable.Failed>(viewModel.state.value.offer)
    }

    @Test
    fun retryingGoesBackToLoadingRatherThanLeavingTheFailureUp() = runTest {
        var attempts = 0
        val viewModel = viewModel(
            catalog = {
                attempts++
                if (attempts == 1) DomainError.StoreUnavailable.left() else offer().right()
            },
        )
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.RetryTapped)
        assertIs<Loadable.Loading>(viewModel.state.value.offer, "a retry has to look like one")

        advanceUntilIdle()
        assertIs<Loadable.Ready<PaywallOffer>>(viewModel.state.value.offer)
    }

    /* ------------------------------ choosing a plan ------------------------------ */

    @Test
    fun selectingAPlanChoosesItByTheStoresIdentifier() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.PlanSelected(MONTHLY_ID))

        assertEquals(MONTHLY_ID, viewModel.state.value.offer.valueOrNull?.selectedPlanId)
    }

    /* ------------------------------ buying ------------------------------ */

    @Test
    fun buyingSendsTheSelectedPlansIdToTheStore() = runTest {
        val bought = mutableListOf<String>()
        val viewModel = viewModel(purchaser = RecordingPurchaser(bought))
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.PlanSelected(MONTHLY_ID))
        viewModel.onEvent(PaywallEvent.StartProTapped)
        advanceUntilIdle()

        assertEquals(listOf(MONTHLY_ID), bought)
    }

    @Test
    fun aCompletedPurchaseClosesThePaywall() = runTest {
        // Nothing else to do: the entitlement stream has already unlocked the screen behind.
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.StartProTapped)
        advanceUntilIdle()

        assertEquals(PaywallEffect.GoBack, viewModel.effects.first())
    }

    @Test
    fun backingOutOfTheStoreSheetSaysNothingAtAll() = runTest {
        // They looked at the price and said no. An error message would be the wrong ending.
        val viewModel = viewModel(purchaser = RefusingPurchaser(DomainError.PaymentCancelled))
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.StartProTapped)
        advanceUntilIdle()

        assertNull(viewModel.state.value.notice)
        assertTrue(!viewModel.state.value.purchasing)
    }

    @Test
    fun aRefusedPaymentSaysSo() = runTest {
        val viewModel = viewModel(purchaser = RefusingPurchaser(DomainError.PaymentFailed))
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.StartProTapped)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.notice != null)
    }

    @Test
    fun aSecondTapWhileTheSheetIsOpenIsIgnored() = runTest {
        val bought = mutableListOf<String>()
        val viewModel = viewModel(purchaser = RecordingPurchaser(bought))
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.StartProTapped)
        viewModel.onEvent(PaywallEvent.StartProTapped)
        advanceUntilIdle()

        assertEquals(1, bought.size, "the store's sheet is modal; a second purchase must not start")
    }

    /* ------------------------------ restoring ------------------------------ */

    @Test
    fun aRestoredSubscriptionClosesThePaywall() = runTest {
        val viewModel = viewModel(purchaser = RestoringPurchaser(RestoreOutcome.ProRestored))
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.RestoreTapped)
        advanceUntilIdle()

        assertEquals(PaywallEffect.GoBack, viewModel.effects.first())
    }

    @Test
    fun anAccountWithNothingToRestoreIsToldPlainly() = runTest {
        // Not a failure: the usual cause is tapping Restore having never subscribed.
        val viewModel = viewModel(purchaser = RestoringPurchaser(RestoreOutcome.NothingToRestore))
        advanceUntilIdle()

        viewModel.onEvent(PaywallEvent.RestoreTapped)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.notice != null)
        assertTrue(!viewModel.state.value.restoring)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun viewModel(
        catalog: SubscriptionCatalog = SubscriptionCatalog { offer().right() },
        purchaser: SubscriptionPurchaser = RecordingPurchaser(mutableListOf()),
        trigger: PaywallTrigger = PaywallTrigger.GENERIC,
    ) = PaywallViewModel(
        catalog = catalog,
        purchaser = purchaser,
        trigger = trigger,
        amountPaise = 0L,
        freeScans = 0,
    )

    private fun offer() = Offer(
        id = "default",
        plans = listOf(
            PlanOption(
                id = MONTHLY_ID,
                period = BillingPeriod.MONTHLY,
                formattedPrice = "₹149.00",
                formattedPricePerMonth = "₹149.00",
                amountMicros = 149_000_000,
                currencyCode = "INR",
                freeTrialDays = 7,
            ),
            PlanOption(
                id = ANNUAL_ID,
                period = BillingPeriod.ANNUAL,
                formattedPrice = "₹1,490.00",
                formattedPricePerMonth = "₹124.17",
                amountMicros = 1_490_000_000,
                currencyCode = "INR",
                freeTrialDays = 7,
            ),
        ),
    )

    private class RecordingPurchaser(private val bought: MutableList<String>) : SubscriptionPurchaser {
        override suspend fun purchase(planId: String): Either<DomainError, Unit> {
            bought += planId
            return Unit.right()
        }

        override suspend fun restore() = RestoreOutcome.NothingToRestore.right()
    }

    private class RefusingPurchaser(private val error: DomainError) : SubscriptionPurchaser {
        override suspend fun purchase(planId: String) = error.left()
        override suspend fun restore() = RestoreOutcome.NothingToRestore.right()
    }

    private class RestoringPurchaser(private val outcome: RestoreOutcome) : SubscriptionPurchaser {
        override suspend fun purchase(planId: String) = Unit.right()
        override suspend fun restore() = outcome.right()
    }

    private companion object {
        const val MONTHLY_ID = "\$rc_monthly"
        const val ANNUAL_ID = "\$rc_annual"
    }
}
