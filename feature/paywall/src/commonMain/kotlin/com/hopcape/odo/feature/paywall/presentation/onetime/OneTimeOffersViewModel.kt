package com.hopcape.odo.feature.paywall.presentation.onetime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import com.hopcape.odo.feature.paywall.presentation.PaywallTelemetry
import com.hopcape.odo.feature.paywall.presentation.state.Loadable
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_ot_error
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
    private val purchaser: OneTimePurchaser,
    private val telemetry: PaywallTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(OneTimeOffersUiState())
    val state: StateFlow<OneTimeOffersUiState> = _state.asStateFlow()

    private val _effects = Channel<OneTimeOffersEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: OneTimeOffersEvent) = when (event) {
        is OneTimeOffersEvent.OfferTapped -> telemetry.oneTimeOfferTapped(event.productId)
        OneTimeOffersEvent.RetryTapped -> load()
        OneTimeOffersEvent.CloseTapped -> dismiss()
    }

    /**
     * Ask the store what each product costs, and keep the ones it answers for.
     *
     * One failed read fails the whole sheet rather than quietly showing two of three: a
     * missing row is indistinguishable from a product that was never created, and the owner
     * would be choosing from a list that is short for a reason nobody told them.
     */
    private fun load() {
        _state.update { it.copy(offers = Loadable.Loading) }
        viewModelScope.launch {
            val priced = runCatchingCancellableSuspend {
                OneTimeOffer.entries.mapNotNull { offer ->
                    purchaser.priceOf(offer.productId)?.let { OneTimeOfferCard(offer, it) }
                }
            }.getOrElse { failure ->
                telemetry.oneTimeOffersUnavailable(failure::class.simpleName ?: UNKNOWN)
                _state.update { it.copy(offers = Loadable.Failed(UiText(Res.string.pw_ot_error))) }
                return@launch
            }
            telemetry.oneTimeOffersShown(count = priced.size)
            _state.update { it.copy(offers = Loadable.Ready(priced)) }
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
