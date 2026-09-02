package com.hopcape.odo.feature.challan.presentation.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.model.Challan
import com.hopcape.odo.core.domain.challan.model.ChallanLookup
import com.hopcape.odo.core.domain.shared.formatDayMonth
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.sum
import com.hopcape.odo.feature.challan.domain.usecase.LookupChallansUseCase
import com.hopcape.odo.feature.challan.presentation.checkedAgo
import com.hopcape.odo.feature.challan.presentation.formatPlate
import com.hopcape.odo.feature.challan.presentation.list.ChallanRow
import com.hopcape.odo.feature.challan.presentation.state.Loadable
import com.hopcape.odo.feature.challan.resources.Res
import com.hopcape.odo.feature.challan.resources.ch_down_title
import com.hopcape.odo.feature.challan.resources.ch_notfound_title
import com.hopcape.odo.feature.challan.resources.ch_result_transfer_badge
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * State holder for a stranger's plate. Fetches on arrival and again on Refresh —
 * remote-only both times, because nothing about this lookup may be cached anywhere
 * (the result screen's own footer promises it).
 *
 * [regNoRaw] arrives normalized from the lookup screen's navigation key.
 */
internal class ChallanResultViewModel(
    regNoRaw: String,
    private val lookup: LookupChallansUseCase,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val regNo: RegistrationNumber? = RegistrationNumber.of(regNoRaw)

    private val _state = MutableStateFlow(ChallanResultUiState(regNo = formatPlate(regNoRaw)))
    val state: StateFlow<ChallanResultUiState> = _state

    private val _effects = Channel<ChallanResultEffect>(Channel.BUFFERED)
    val effects: Flow<ChallanResultEffect> = _effects.receiveAsFlow()

    /** When the shown answer was fetched — what "Checked just now" ages against. */
    private var answeredAt: Instant? = null

    init {
        fetch(initial = true)
    }

    fun onEvent(event: ChallanResultEvent) {
        when (event) {
            ChallanResultEvent.BackTapped,
            ChallanResultEvent.CheckAnotherTapped -> _effects.trySend(ChallanResultEffect.NavigateBack)

            ChallanResultEvent.RefreshTapped -> fetch(initial = false)
        }
    }

    private fun fetch(initial: Boolean) {
        val plate = regNo ?: run {
            _state.update { it.copy(content = Loadable.Failed(UiText(Res.string.ch_notfound_title))) }
            return
        }
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = !initial) }
            lookup(plate).fold(
                ifLeft = {
                    _state.update { current ->
                        current.copy(
                            refreshing = false,
                            // A refresh that failed keeps the answer on screen; only the
                            // first fetch failing has nothing better to show.
                            content = if (current.content is Loadable.Ready) {
                                current.content
                            } else {
                                Loadable.Failed(UiText(Res.string.ch_down_title))
                            },
                        )
                    }
                },
                ifRight = { answer ->
                    answeredAt = clock.now()
                    _state.update { current ->
                        current.copy(refreshing = false, content = Loadable.Ready(answer.toContent()))
                    }
                },
            )
        }
    }

    private fun ChallanLookup.toContent(): ChallanResultContent {
        val pending = when (this) {
            // A plate that disappeared between the lookup and a refresh reads as clean —
            // the honest floor for a source that just said it knows nothing.
            ChallanLookup.VehicleNotFound -> emptyList()
            is ChallanLookup.Found -> challans.filter { it.isPayableOnline }
        }
        return ChallanResultContent(
            checkedAgo = checkedAgo(answeredAt ?: clock.now(), clock.now()),
            transfer = pending.takeIf { it.isNotEmpty() }?.let { rows ->
                TransferWarning(
                    badge = UiText(
                        Res.string.ch_result_transfer_badge,
                        listOf(rows.size, rows.map { it.amount }.sum().formatRupees()),
                    ),
                )
            },
            rows = pending.map { it.toRow() },
        )
    }

    private fun Challan.toRow() = ChallanRow(
        id = id.value,
        violation = violation,
        number = id.value,
        amount = amount.formatRupees(),
        location = location,
        date = formatDayMonth(issuedOn),
    )
}
