package com.hopcape.odo.feature.support.presentation.flagprice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.support.TicketDetail
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.feature.support.domain.usecase.PickedFile
import com.hopcape.odo.feature.support.domain.usecase.SubmitTicketUseCase
import com.hopcape.odo.feature.support.presentation.SupportTelemetry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface FlagPriceEffect {

    data object NavigateBack : FlagPriceEffect

    data object PickBill : FlagPriceEffect

    /** Filed. No confirmation screen: the footer already says no answer is coming. */
    data object Sent : FlagPriceEffect

    data object Failed : FlagPriceEffect
}

/**
 * A price correction.
 *
 * **No reply address is carried.** The screen says outright that nobody will be emailed about
 * it, and a correction is a data point rather than a conversation — attaching an address to
 * one would be collecting something with no use for it.
 */
internal class FlagPriceViewModel(
    band: DisputedBand?,
    private val submit: SubmitTicketUseCase,
    private val telemetry: SupportTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(FlagPriceUiState(band = band))
    val state: StateFlow<FlagPriceUiState> = _state.asStateFlow()

    private val _effects = Channel<FlagPriceEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: FlagPriceEvent) {
        when (event) {
            FlagPriceEvent.BackClicked -> emit(FlagPriceEffect.NavigateBack)
            is FlagPriceEvent.JobNameChanged -> _state.update { it.copy(jobName = event.name) }
            is FlagPriceEvent.ComplaintPicked ->
                _state.update { it.copy(complaint = event.complaint) }
            // Capped as well as filtered to digits. Twenty digits overflow a Long, and a
            // figure that will not parse is a correction the panel cannot act on.
            is FlagPriceEvent.PaidChanged ->
                _state.update { it.copy(paidRupees = event.rupees.take(MAX_PAID_DIGITS)) }
            FlagPriceEvent.AttachBillClicked -> emit(FlagPriceEffect.PickBill)
            is FlagPriceEvent.BillPicked -> _state.update { it.copy(billRef = event.ref) }
            FlagPriceEvent.SendClicked -> send()
        }
    }

    private fun send() {
        val current = _state.value
        // The complaint and the figure are what the screen requires, and this is what makes
        // the button's own rule true for a send raised any other way — including two taps
        // delivered before it redraws.
        if (!current.canSend) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            telemetry.timingSubmit {
                submit(
                    kind = TicketKind.PRICE_CORRECTION,
                    // The figures are the correction; the body is what a person reads first.
                    body = current.body(),
                    details = current.details(),
                    picked = current.billRef
                        ?.let { listOf(PickedFile(ref = it, name = BILL_NAME)) }
                        .orEmpty(),
                )
            }.fold(
                ifLeft = { error ->
                    telemetry.submitFailed(TicketKind.PRICE_CORRECTION, error)
                    _state.update { it.copy(sending = false) }
                    emit(FlagPriceEffect.Failed)
                },
                ifRight = { ticket ->
                    telemetry.ticketSubmitted(
                        kind = TicketKind.PRICE_CORRECTION,
                        // What was stored, not what was picked: a bill that would not copy is
                        // dropped, and counting it says a photograph reached support.
                        attachments = ticket.attachments.size,
                        logsAttached = false,
                    )
                    _state.update { it.copy(sending = false) }
                    emit(FlagPriceEffect.Sent)
                },
            )
        }
    }

    private fun emit(effect: FlagPriceEffect) {
        _effects.trySend(effect)
    }

    private companion object {
        const val BILL_NAME = "bill"

        /** Rs. 99,99,99,999 is past any service bill, and short of a Long in paise. */
        const val MAX_PAID_DIGITS = 9
    }
}

/**
 * The sentence a person reads before the fields.
 *
 * Assembled rather than typed, because this form has no message box — the owner answers three
 * questions and the useful part is the number. A body of some kind is required, and a
 * generated one beats an empty row.
 */
private fun FlagPriceUiState.body(): String {
    val job = band?.lineName ?: jobName
    val complaint = complaint?.name?.lowercase()?.replace('_', ' ').orEmpty()
    return "Band for $job looks $complaint. Actually paid Rs. $paidRupees."
}

private fun FlagPriceUiState.details(): Map<String, String> = buildMap {
    put(TicketDetail.JOB, band?.lineName ?: jobName)
    complaint?.let { put(TicketDetail.COMPLAINT, it.name) }
    // Paise, like every other money column in the schema. The state parses it, so this and
    // the button's own rule cannot disagree about whether there is a figure.
    paidPaise?.let { put(TicketDetail.PAID_PAISE, it.toString()) }
    band?.let {
        put(TicketDetail.BAND_LOW_PAISE, it.lowPaise.toString())
        put(TicketDetail.BAND_HIGH_PAISE, it.highPaise.toString())
        it.city?.let { city -> put(TicketDetail.CITY, city) }
        it.workshop?.let { tier -> put(TicketDetail.WORKSHOP_TIER, tier) }
        it.segment?.let { segment -> put(TicketDetail.SEGMENT, segment) }
    }
}

