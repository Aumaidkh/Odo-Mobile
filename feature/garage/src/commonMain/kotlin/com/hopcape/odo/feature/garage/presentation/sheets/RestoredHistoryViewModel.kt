package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.feature.garage.domain.usecase.AcknowledgeRestoredHistoryUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ObserveRestoredHistoryUseCase
import com.hopcape.odo.feature.garage.domain.usecase.UpdateOdometerUseCase
import com.hopcape.odo.feature.garage.presentation.GarageTelemetry
import com.hopcape.odo.feature.garage.presentation.state.Submission
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_save_failed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the sheet that tells the owner their history came back (issue #425).
 *
 * The restore is acknowledged whichever way the sheet is closed, including the odometer
 * being adopted. A sheet that came back on the next launch because the owner tapped the
 * "wrong" button would be worse than never showing it.
 */
internal class RestoredHistoryViewModel(
    private val observeRestored: ObserveRestoredHistoryUseCase,
    private val acknowledge: AcknowledgeRestoredHistoryUseCase,
    private val updateOdometer: UpdateOdometerUseCase,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(RestoredHistoryUiState())
    val state: StateFlow<RestoredHistoryUiState> = _state.asStateFlow()

    private val _effects = Channel<RestoredHistoryEffect>(Channel.BUFFERED)
    val effects: Flow<RestoredHistoryEffect> = _effects.receiveAsFlow()

    /** Counted once, not on every re-emission of the same restore as its counts settle. */
    private var reported = false

    init {
        viewModelScope.launch {
            observeRestored()
                .catch { cause -> telemetry.readFailed(GarageTelemetry.Screen.HISTORY_RESTORED, cause) }
                .collect { history ->
                    if (history != null && !reported) {
                        reported = true
                        telemetry.historyRestoredShown(
                            services = history.serviceLogCount,
                            documents = history.documentCount,
                            correctionOffered = history.odometer != null,
                        )
                    }
                    _state.update { it.copy(history = history) }
                }
        }
    }

    fun onEvent(event: RestoredHistoryEvent) = when (event) {
        RestoredHistoryEvent.AdoptOdometerTapped -> adopt()
        RestoredHistoryEvent.DismissTapped -> close(adopted = false, RestoredHistoryEffect.Dismiss)
        RestoredHistoryEvent.ViewHistoryTapped -> viewHistory()
    }

    /**
     * Take the history's reading.
     *
     * It still goes through [UpdateOdometerUseCase], which refuses anything that would move
     * the odometer backwards. The policy has already ruled that out, so this is the second
     * of two locks rather than the only one — a correction is a write like any other.
     */
    private fun adopt() {
        val history = _state.value.history ?: return
        val correction = history.odometer ?: return

        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(GarageTelemetry.Trace.SAVE_ODOMETER)) {
            // The same wrapper the odometer sheet uses, so a correction the domain refuses
            // is counted beside the ones an owner typed rather than in a category of its own.
            telemetry.odometerSave { updateOdometer(history.carId, correction.historyKm) }.fold(
                ifLeft = {
                    _state.update {
                        it.copy(submission = Submission.Failed(UiText(Res.string.gr_error_save_failed)))
                    }
                },
                ifRight = { close(adopted = true, RestoredHistoryEffect.Dismiss) },
            )
        }
    }

    private fun viewHistory() {
        val carId = _state.value.history?.carId ?: return
        close(adopted = false, RestoredHistoryEffect.OpenHistory(carId.value))
    }

    /** Forget the restore, count the answer, and let the host close the sheet. */
    private fun close(adopted: Boolean, effect: RestoredHistoryEffect) {
        telemetry.historyRestoredAnswered(adopted)
        viewModelScope.launch {
            acknowledge()
            _effects.send(effect)
        }
    }
}
