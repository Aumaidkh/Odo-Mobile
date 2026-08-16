package com.hopcape.odo.feature.refuel.presentation.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.refuel.PendingFill
import com.hopcape.odo.core.domain.refuel.PendingFillStore
import com.hopcape.odo.feature.refuel.domain.DraftPayload
import com.hopcape.odo.feature.refuel.domain.toInput
import com.hopcape.odo.feature.refuel.domain.usecase.ResolvePendingFillUseCase
import com.hopcape.odo.feature.refuel.presentation.RefuelTelemetry
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatRupees
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * State holder for the unanswered-detections sheet.
 *
 * Nothing here writes a fill. Reviewing a row hands it to the confirm surface, which is the
 * one place a fill is ever created — the same route a notification's Edit takes, and the same
 * one the pump scanner uses. This sheet only decides *which* detection is being answered.
 *
 * A row the owner rejects is resolved rather than deleted, so the payment behind it cannot be
 * offered again the next time the listener re-reads the shade.
 */
internal class PendingFillsViewModel(
    private val pending: PendingFillStore,
    private val resolvePendingFill: ResolvePendingFillUseCase,
    private val telemetry: RefuelTelemetry,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(PendingFillsUiState())
    val state: StateFlow<PendingFillsUiState> = _state.asStateFlow()

    private val _effects = Channel<PendingFillsEffect>(Channel.BUFFERED)
    val effects: Flow<PendingFillsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch(telemetry.op(OP_LOAD)) {
            pending.observeOpen().collect { fills ->
                _state.update { it.copy(loading = false, fills = fills.map { row -> row.toRow() }) }
                // Answering the last one ends the conversation. Leaving an empty sheet open
                // would make the owner dismiss a screen that is telling them nothing.
                if (fills.isEmpty() && !_state.value.loading) {
                    _effects.trySend(PendingFillsEffect.Dismiss)
                }
            }
        }
    }

    fun onEvent(event: PendingFillsEvent) = when (event) {
        is PendingFillsEvent.ReviewTapped -> review(event.id)
        is PendingFillsEvent.DismissTapped -> reject(event.id)
        PendingFillsEvent.CloseTapped -> {
            _effects.trySend(PendingFillsEffect.Dismiss)
            Unit
        }
    }

    /**
     * Hand one detection to the confirm step.
     *
     * The row is *not* resolved here. The owner may back out of the confirm surface without
     * writing anything, and a question marked answered on the way to answering it is one they
     * can never come back to. It resolves when the fill is actually written.
     */
    private fun review(id: String) {
        val row = _state.value.fills.firstOrNull { it.id == id } ?: return
        val draft = DraftPayload.decode(row.draftPayload)
        if (draft == null) {
            // A payload this build cannot read is a question that can never be answered, so
            // it is closed rather than left in the list forever.
            viewModelScope.launch(telemetry.op(OP_RESOLVE)) { resolvePendingFill(id) }
            return
        }
        _effects.trySend(PendingFillsEffect.Review(draft.toInput()))
    }

    private fun reject(id: String) {
        viewModelScope.launch(telemetry.op(OP_RESOLVE)) {
            telemetry.captureRejected(SOURCE_PENDING)
            resolvePendingFill(id)
        }
    }

    /**
     * The same formatters the rest of the app uses.
     *
     * "Rs. 3100" beside a payment app's own "Rs. 3,100", or an ISO date under a merchant name,
     * both read as something a machine printed — on the one screen whose job is to convince
     * the owner these are their own payments.
     */
    private fun PendingFill.toRow() = PendingFillRow(
        id = id,
        merchant = merchant.orEmpty(),
        amountLabel = amount?.formatRupees().orEmpty(),
        whenLabel = formatDate(detectedAt.toLocalDateTime(timeZone).date),
        draftPayload = draftPayload,
    )

    private companion object {
        const val OP_LOAD = "refuel_pending_load"
        const val OP_RESOLVE = "refuel_pending_resolve"

        /** Reported apart from a rejection on the notification: a later answer is a different act. */
        const val SOURCE_PENDING = "PENDING"
    }
}
