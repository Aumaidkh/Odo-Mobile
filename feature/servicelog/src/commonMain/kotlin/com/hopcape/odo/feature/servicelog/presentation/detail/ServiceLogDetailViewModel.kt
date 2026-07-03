package com.hopcape.odo.feature.servicelog.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.servicelog.domain.usecase.DeleteServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ReportOverchargeUseCase
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

internal class ServiceLogDetailViewModel(
    private val getLog: GetServiceLogUseCase,
    private val deleteLog: DeleteServiceLogUseCase,
    private val resolveFairness: ResolveEntryFairnessUseCase,
    private val reportOvercharge: ReportOverchargeUseCase,
    private val cityProvider: CurrentCityProvider,
    private val telemetry: ServiceLogTelemetry,
    private val logId: ServiceLogId,
) : ViewModel() {

    private val _state = MutableStateFlow(ServiceLogDetailUiState())
    val state: StateFlow<ServiceLogDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<ServiceLogDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ServiceLogDetailEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            getLog(logId).collect { entry ->
                val content = if (entry == null) {
                    ServiceLogDetailUiState.Content.NotFound
                } else {
                    ServiceLogDetailUiState.Content.Loaded(
                        entry = entry,
                        fairness = resolveFairness(entry, cityProvider.currentCity()),
                    )
                }
                _state.update { it.copy(content = content) }
            }
        }
    }

    fun onEvent(event: ServiceLogDetailEvent) {
        when (event) {
            ServiceLogDetailEvent.EditClicked -> emit(ServiceLogDetailEffect.OpenEdit(logId))
            ServiceLogDetailEvent.DeleteClicked -> _state.update { it.copy(showDeleteConfirm = true) }
            ServiceLogDetailEvent.DismissDelete -> _state.update { it.copy(showDeleteConfirm = false) }
            ServiceLogDetailEvent.ConfirmDelete -> confirmDelete()
            ServiceLogDetailEvent.ReportOverchargeClicked -> report()
            ServiceLogDetailEvent.Back -> emit(ServiceLogDetailEffect.Back)
        }
    }

    private fun confirmDelete() {
        _state.update { it.copy(showDeleteConfirm = false, isDeleting = true) }
        viewModelScope.launch {
            deleteLog(logId).fold(
                ifLeft = { _state.update { it.copy(isDeleting = false) } },
                ifRight = {
                    telemetry.deleted()
                    _effects.send(ServiceLogDetailEffect.Back)
                },
            )
        }
    }

    private fun report() {
        val entry = (_state.value.content as? ServiceLogDetailUiState.Content.Loaded)?.entry ?: return
        viewModelScope.launch {
            reportOvercharge(OverchargeReport(logId = entry.id, category = entry.categories.singleOrNull()))
                .fold(
                    ifLeft = { /* left quiet for now; a retry surface can come later */ },
                    ifRight = { _state.update { it.copy(reported = true) } },
                )
        }
    }

    private fun emit(effect: ServiceLogDetailEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
