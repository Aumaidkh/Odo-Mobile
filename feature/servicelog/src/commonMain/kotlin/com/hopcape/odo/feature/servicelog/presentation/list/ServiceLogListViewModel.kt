package com.hopcape.odo.feature.servicelog.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.model.ServiceRecordSummary
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveServiceLogsUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ResolveEntryFairnessUseCase
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the service-log ledger. Observes the car's entries, resolves each
 * entry's fairness verdict, derives the [ServiceRecordSummary] + [FairnessSavings], and
 * applies the [ServiceLogFilter] locally.
 */
internal class ServiceLogListViewModel(
    private val observeLogs: ObserveServiceLogsUseCase,
    private val resolveFairness: ResolveEntryFairnessUseCase,
    private val cityProvider: CurrentCityProvider,
    private val telemetry: ServiceLogTelemetry,
    private val carId: CarId,
) : ViewModel() {

    private val _state = MutableStateFlow(ServiceLogListUiState())
    val state: StateFlow<ServiceLogListUiState> = _state.asStateFlow()

    private val _effects = Channel<ServiceLogListEffect>(Channel.BUFFERED)
    val effects: Flow<ServiceLogListEffect> = _effects.receiveAsFlow()

    private var allRows: List<LedgerRow> = emptyList()
    private var viewLogged = false

    init {
        viewModelScope.launch {
            observeLogs(carId).collect { entries ->
                val city = cityProvider.currentCity()
                allRows = entries.map { LedgerRow(it, resolveFairness(it, city)) }
                _state.update { it.copy(content = contentFor(allRows, it.filter)) }
                if (!viewLogged) {
                    viewLogged = true
                    val summary = ServiceRecordSummary.of(entries)
                    telemetry.listViewed(summary.serviceCount, summary.verifiedCount)
                }
            }
        }
    }

    fun onEvent(event: ServiceLogListEvent) {
        when (event) {
            is ServiceLogListEvent.FilterChanged ->
                _state.update { it.copy(filter = event.filter, content = contentFor(allRows, event.filter)) }
            is ServiceLogListEvent.LogClicked -> emit(ServiceLogListEffect.OpenDetail(event.id))
            ServiceLogListEvent.AddClicked -> emit(ServiceLogListEffect.OpenAdd)
            ServiceLogListEvent.ScanClicked -> Unit // coming soon (M2)
            ServiceLogListEvent.Back -> emit(ServiceLogListEffect.Back)
        }
    }

    /** Empty list → [ServiceLogListUiState.Content.Empty]; otherwise the ledger + filtered rows. */
    private fun contentFor(rows: List<LedgerRow>, filter: ServiceLogFilter): ServiceLogListUiState.Content {
        if (rows.isEmpty()) return ServiceLogListUiState.Content.Empty

        val over = rows.mapNotNull { it.fairness as? FairnessVerdict.Over }
        val savings = if (over.isEmpty()) {
            FairnessSavings.NONE
        } else {
            FairnessSavings(over.fold(Amount.ZERO) { acc, v -> acc + v.by }, over.size)
        }
        return ServiceLogListUiState.Content.Ledger(
            summary = ServiceRecordSummary.of(rows.map { it.entry }),
            savings = savings,
            flaggedCount = over.size,
            visible = visibleRows(rows, filter),
        )
    }

    private fun visibleRows(rows: List<LedgerRow>, filter: ServiceLogFilter): List<LedgerRow> =
        when (filter) {
            ServiceLogFilter.ALL -> rows
            ServiceLogFilter.VERIFIED -> rows.filter { it.entry.verification == VerificationStatus.VERIFIED }
            ServiceLogFilter.FLAGGED -> rows.filter { it.fairness is FairnessVerdict.Over }
        }

    private fun emit(effect: ServiceLogListEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
