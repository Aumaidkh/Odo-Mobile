package com.hopcape.odo.feature.paywall.presentation.onetime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.designsystem.text.UiText
import arrow.core.left
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.scan.entitlement.ScanCredits
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import com.hopcape.odo.feature.paywall.presentation.PaywallTelemetry
import com.hopcape.odo.feature.paywall.presentation.state.Loadable
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_ot_error
import com.hopcape.odo.feature.paywall.resources.pw_ot_not_yet
import com.hopcape.odo.feature.paywall.resources.pw_ot_purchase_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the one-time offers sheet.
 *
 * It reads what each product costs and shows the ones the store knows about. **It does not
 * buy anything yet** — the purchase path and the balances a purchase would credit are the
 * next slice, and selling a bill-check pack before there is anywhere to put the credits
 * would take money and grant nothing. Until then a tap is a signal, not a checkout, which is
 * why [PaywallTelemetry.oneTimeOfferTapped] is the only thing it does.
 *
 * A product with no price is dropped rather than shown. None of the three exist in Play
 * Console yet, so today this sheet loads empty — deliberately, and visibly, rather than
 * inventing a figure the store never gave.
 */
internal class OneTimeOffersViewModel(
    private val context: OneTimeContext,
    private val purchaser: OneTimePurchaser,
    private val credits: ScanCredits,
    private val telemetry: PaywallTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(OneTimeOffersUiState(context = context))
    val state: StateFlow<OneTimeOffersUiState> = _state.asStateFlow()

    private val _effects = Channel<OneTimeOffersEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    /** The in-flight read, held so a retry cannot be overtaken by the attempt it replaced. */
    private var loadJob: Job? = null

    /** Whether the sheet has already been reported. Retrying is not a second opening. */
    private var reported = false

    init {
        load()
    }

    fun onEvent(event: OneTimeOffersEvent) = when (event) {
        is OneTimeOffersEvent.OfferTapped -> buy(event.productId)
        OneTimeOffersEvent.RetryTapped -> load()
        OneTimeOffersEvent.CloseTapped -> dismiss()
    }

    /**
     * Ask the store what the products cost, in one call, and keep the ones it answers for.
     *
     * A store that could not be asked fails the whole sheet rather than quietly showing two
     * of three: a missing row is indistinguishable from a product that was never created, and
     * the owner would be choosing from a list that is short for a reason nobody told them.
     * That is why this reads [OneTimePurchaser.pricesOf] and not `priceOf` — the latter
     * collapses "no such product" and "could not ask" into the same null, which is exactly
     * the distinction the sheet is built on.
     */
    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(offers = Loadable.Loading) }
        loadJob = viewModelScope.launch {
            val prices = runCatchingCancellableSuspend {
                purchaser.pricesOf(context.offers.map { it.productId })
            }.getOrElse { failure -> DomainError.StoreUnavailable.left() }

            prices.fold(
                ifLeft = { failed(it) },
                ifRight = { priced -> show(priced) },
            )
        }
    }

    private fun show(prices: Map<String, String>) {
        val cards = context.offers.mapNotNull { offer ->
            prices[offer.productId]?.let {
                OneTimeOfferCard(offer, it, recommended = offer == context.recommended)
            }
        }
        // Once per sheet, not once per attempt: a retry is the same opening, and counting it
        // twice would make "how many were shown nothing" a number nobody can read.
        if (!reported) {
            reported = true
            telemetry.oneTimeOffersShown(count = cards.size)
        }
        _state.update { it.copy(offers = Loadable.Ready(cards)) }
    }

    private fun failed(error: DomainError) {
        telemetry.oneTimeOffersUnavailable(error::class.simpleName ?: UNKNOWN)
        _state.update { it.copy(offers = Loadable.Failed(UiText(Res.string.pw_ot_error))) }
    }

    /**
     * Take the owner through the store, then credit what they bought.
     *
     * The grant follows the store's confirmation rather than the tap, and it is this
     * screen's job rather than the purchaser's: two answers to "did a purchase happen"
     * diverge the first time one completes somewhere else.
     *
     * A product this sheet cannot grant is never charged for. The export sells and credits
     * itself from the share sheet, and taking money here for a balance nothing would raise
     * is the one failure worth refusing outright.
     */
    private fun buy(productId: String) {
        if (_state.value.purchasing) return
        val offer = OneTimeOffer.entries.firstOrNull { it.productId == productId } ?: return
        telemetry.oneTimeOfferTapped(productId)
        if (!offer.purchasable) {
            _state.update { it.copy(notice = UiText(Res.string.pw_ot_not_yet)) }
            return
        }
        _state.update { it.copy(purchasing = true, notice = null) }
        viewModelScope.launch {
            runCatchingCancellableSuspend { purchaser.purchase(productId) }
                .getOrElse { DomainError.PaymentFailed.left() }
                .fold(ifLeft = { refused(productId, it) }, ifRight = { granted(offer) })
        }
    }

    private suspend fun granted(offer: OneTimeOffer) {
        credits.grant(offer.scanCredits)
        telemetry.oneTimePurchaseCompleted(offer.productId)
        _state.update { it.copy(purchasing = false) }
        _effects.trySend(OneTimeOffersEffect.Dismiss)
    }

    /**
     * Backing out is not a failure — it is the most common ending a store sheet has, and
     * putting an error in front of someone who changed their mind is the wrong reply.
     */
    private fun refused(productId: String, error: DomainError) {
        val cancelled = error == DomainError.PaymentCancelled
        if (cancelled) telemetry.oneTimePurchaseCancelled(productId)
        else telemetry.oneTimePurchaseFailed(productId)
        _state.update {
            it.copy(
                purchasing = false,
                notice = if (cancelled) null else UiText(Res.string.pw_ot_purchase_failed),
            )
        }
    }

    private fun dismiss() {
        _effects.trySend(OneTimeOffersEffect.Dismiss)
    }

    private companion object {
        /** Stands in for a failure whose class has no name, so a field is never missing. */
        const val UNKNOWN = "Unknown"
    }
}
