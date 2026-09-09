package com.hopcape.odo.feature.challan.presentation.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.model.ChallanLookup
import com.hopcape.odo.feature.challan.domain.usecase.LookupChallansUseCase
import com.hopcape.odo.feature.challan.presentation.ChallanTelemetry
import com.hopcape.odo.feature.challan.presentation.formatPlate
import com.hopcape.odo.feature.challan.resources.Res
import com.hopcape.odo.feature.challan.resources.ch_down_title
import com.hopcape.odo.feature.challan.resources.ch_lookup_invalid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the buyer's check. Holds [ChallanLookupUiState], consumes
 * [ChallanLookupEvent]s and emits [ChallanLookupEffect]s.
 *
 * The lookup itself never writes anything — the privacy card's "Nothing saved" is kept
 * by the use case, and this ViewModel adds nothing to break it: even the typed plate
 * lives only in this screen's state.
 */
internal class ChallanLookupViewModel(
    private val lookup: LookupChallansUseCase,
    private val telemetry: ChallanTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ChallanLookupUiState())
    val state: StateFlow<ChallanLookupUiState> = _state

    private val _effects = Channel<ChallanLookupEffect>(Channel.BUFFERED)
    val effects: Flow<ChallanLookupEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ChallanLookupEvent) {
        when (event) {
            ChallanLookupEvent.BackTapped -> _effects.trySend(ChallanLookupEffect.NavigateBack)
            is ChallanLookupEvent.PlateChanged ->
                _state.update { it.copy(plate = event.value.uppercase(), error = null) }

            ChallanLookupEvent.CheckTapped -> check()
            ChallanLookupEvent.EditNumberTapped -> _state.update { it.copy(notFound = null) }
        }
    }

    private fun check() {
        val current = _state.value
        if (current.checking) return
        val regNo = RegistrationNumber.of(current.plate)
        if (regNo == null || !current.canCheck) {
            _state.update { it.copy(error = UiText(Res.string.ch_lookup_invalid)) }
            return
        }
        viewModelScope.launch {
            telemetry.lookupSubmitted()
            _state.update { it.copy(checking = true, error = null) }
            lookup(regNo).fold(
                ifLeft = {
                    telemetry.lookupAnswered(OUTCOME_UNREACHABLE)
                    _state.update { it.copy(checking = false, error = UiText(Res.string.ch_down_title)) }
                },
                ifRight = { answer ->
                    when (answer) {
                        ChallanLookup.VehicleNotFound -> {
                            telemetry.lookupAnswered(OUTCOME_NOT_FOUND)
                            _state.update {
                                it.copy(checking = false, notFound = NotFoundState(formatPlate(regNo.value)))
                            }
                        }

                        is ChallanLookup.Found -> {
                            telemetry.lookupAnswered(
                                if (answer.challans.any { it.isPayableOnline }) OUTCOME_FOUND else OUTCOME_CLEAN,
                            )
                            _state.update { it.copy(checking = false) }
                            _effects.trySend(ChallanLookupEffect.OpenResult(regNo.value))
                        }
                    }
                },
            )
        }
    }

    private companion object {
        const val OUTCOME_FOUND = "found"
        const val OUTCOME_CLEAN = "clean"
        const val OUTCOME_NOT_FOUND = "not_found"
        const val OUTCOME_UNREACHABLE = "unreachable"
    }
}
