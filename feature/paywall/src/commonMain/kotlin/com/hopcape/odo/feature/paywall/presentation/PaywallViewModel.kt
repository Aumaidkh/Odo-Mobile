package com.hopcape.odo.feature.paywall.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.Offer
import com.hopcape.odo.core.domain.subscription.PlanOption
import com.hopcape.odo.core.domain.subscription.RestoreOutcome
import com.hopcape.odo.core.domain.subscription.SubscriptionCatalog
import com.hopcape.odo.core.domain.subscription.SubscriptionPurchaser
import com.hopcape.odo.feature.paywall.presentation.state.Loadable
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_error_nothing_for_sale
import com.hopcape.odo.feature.paywall.resources.pw_error_store_unavailable
import com.hopcape.odo.feature.paywall.resources.pw_purchase_failed
import com.hopcape.odo.feature.paywall.resources.pw_restore_failed
import com.hopcape.odo.feature.paywall.resources.pw_restore_none
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The paywall's state: what is for sale, which plan is chosen, and what the store just said.
 *
 * The offer is read on open and on every retry, never cached. Prices, trial eligibility and
 * which plans exist all belong to the store, and a remembered offer is a price this screen is
 * no longer sure of.
 *
 * **Nothing here reports what the owner is now entitled to.** A completed purchase pushes a
 * new value onto the entitlement stream, so the screen underneath has already unlocked by the
 * time this closes. Asking again from here would be a second answer to one question.
 */
internal class PaywallViewModel(
    private val catalog: SubscriptionCatalog,
    private val purchaser: SubscriptionPurchaser,
    private val telemetry: PaywallTelemetry,
    private val trigger: PaywallTrigger,
    amountPaise: Long,
    freeScans: Int,
) : ViewModel() {

    /** Which surface sent the owner here, on every event. The paywall cannot see it itself. */
    private val from: String get() = trigger.name

    private val _state = MutableStateFlow(
        PaywallUiState(trigger = trigger, amountPaise = amountPaise, freeScans = freeScans),
    )
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    private val _effects = Channel<PaywallEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    init {
        telemetry.shown(from)
        loadOffer()
    }

    fun onEvent(event: PaywallEvent) {
        when (event) {
            PaywallEvent.CloseTapped -> {
                telemetry.dismissed(from)
                send(PaywallEffect.GoBack)
            }
            PaywallEvent.RetryTapped -> loadOffer()
            is PaywallEvent.PlanSelected -> selectPlan(event.planId)
            PaywallEvent.StartProTapped -> startPurchase()
            PaywallEvent.RestoreTapped -> restore()
        }
    }

    /**
     * Read the offer.
     *
     * Back to [Loadable.Loading] first, so a retry looks like one — leaving the old failure on
     * screen while the request runs would make the button look dead.
     */
    private fun loadOffer() {
        _state.value = _state.value.copy(offer = Loadable.Loading, notice = null)
        viewModelScope.launch {
            catalog.current().fold(
                ifLeft = { error ->
                    telemetry.offerUnavailable(from, error.reasonName())
                    _state.value = _state.value.copy(offer = Loadable.Failed(error.toMessage()))
                },
                ifRight = { offer -> _state.value = _state.value.copy(offer = Loadable.Ready(offer.toUi())) },
            )
        }
    }

    private fun selectPlan(planId: String) {
        val offer = _state.value.offer
        if (offer !is Loadable.Ready) return
        if (offer.value.selectedPlanId == planId) return
        telemetry.planSelected(from, planId)
        _state.value = _state.value.copy(
            offer = Loadable.Ready(offer.value.copy(selectedPlanId = planId)),
            notice = null,
        )
    }

    /**
     * Buy the selected plan.
     *
     * Refuses while anything else is in flight. The store's sheet is modal, so a second tap
     * would either be swallowed or start a purchase behind the one already open.
     */
    private fun startPurchase() {
        val plan = (_state.value.offer as? Loadable.Ready)?.value?.selected ?: return
        if (_state.value.busy) return

        val withTrial = plan.trialDays != null
        telemetry.checkoutStarted(from, plan.id, withTrial)
        _state.value = _state.value.copy(purchasing = true, notice = null)
        viewModelScope.launch {
            purchaser.purchase(plan.id).fold(
                ifLeft = { error ->
                    if (error == DomainError.PaymentCancelled) {
                        telemetry.purchaseCancelled(from, plan.id)
                    } else {
                        telemetry.purchaseFailed(from, plan.id)
                    }
                    _state.value = _state.value.copy(
                        purchasing = false,
                        // Backing out is not a failure and gets no message. They looked at the
                        // price and said no; telling them off for it would be the wrong ending.
                        notice = if (error == DomainError.PaymentCancelled) null else UiText(Res.string.pw_purchase_failed),
                    )
                },
                ifRight = {
                    telemetry.purchaseCompleted(from, plan.id, withTrial)
                    _state.value = _state.value.copy(purchasing = false)
                    send(PaywallEffect.GoBack)
                },
            )
        }
    }

    private fun restore() {
        if (_state.value.busy) return
        telemetry.restoreTapped(from)
        _state.value = _state.value.copy(restoring = true, notice = null)
        viewModelScope.launch {
            purchaser.restore().fold(
                ifLeft = {
                    telemetry.restoreFinished(from, restored = false)
                    finishRestore(UiText(Res.string.pw_restore_failed))
                },
                ifRight = { outcome ->
                    when (outcome) {
                        // Nothing else to do: the entitlement stream has already changed, so
                        // the screen behind this one is unlocked. Say so and get out of the way.
                        RestoreOutcome.ProRestored -> {
                            telemetry.restoreFinished(from, restored = true)
                            _state.value = _state.value.copy(restoring = false)
                            send(PaywallEffect.GoBack)
                        }
                        // Not an error — the usual cause is tapping Restore on an account that
                        // never subscribed — so it gets a plain sentence, not a failure.
                        RestoreOutcome.NothingToRestore -> {
                            telemetry.restoreFinished(from, restored = false)
                            finishRestore(UiText(Res.string.pw_restore_none))
                        }
                    }
                },
            )
        }
    }

    private fun finishRestore(notice: UiText) {
        _state.value = _state.value.copy(restoring = false, notice = notice)
    }

    /**
     * The offer as the screen draws it.
     *
     * The annual plan is preselected when there is one. It is the better deal for the owner
     * and the one the saving badge is about, so opening on it is what the rest of the screen
     * already says.
     */
    private fun Offer.toUi(): PaywallOffer {
        val cards = plans.map { it.toCard() }
        return PaywallOffer(
            plans = cards,
            selectedPlanId = (annual ?: monthly ?: plans.first()).id,
            savingPercent = annualSavingPercent,
        )
    }

    private fun PlanOption.toCard() = PaywallPlanCard(
        id = id,
        period = period,
        price = formattedPrice,
        pricePerMonth = formattedPricePerMonth,
        trialDays = freeTrialDays,
    )

    /**
     * Why there is nothing to show.
     *
     * The two are kept apart because only one of them is worth a retry: a request that did not
     * come back will usually work a moment later, and a dashboard with nothing in it will not.
     */
    /** The store's reason, as a stable analytics value rather than a message. */
    private fun DomainError.reasonName(): String =
        if (this == DomainError.NothingForSale) REASON_NOTHING_FOR_SALE else REASON_STORE_UNAVAILABLE

    private fun DomainError.toMessage(): UiText = when (this) {
        DomainError.NothingForSale -> UiText(Res.string.pw_error_nothing_for_sale)
        else -> UiText(Res.string.pw_error_store_unavailable)
    }

    private fun send(effect: PaywallEffect) {
        _effects.trySend(effect)
    }

    private companion object {
        const val REASON_NOTHING_FOR_SALE = "nothing_for_sale"
        const val REASON_STORE_UNAVAILABLE = "store_unavailable"
    }
}
