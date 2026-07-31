package com.hopcape.odo.feature.servicelog.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.feature.servicelog.domain.usecase.DeleteServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveEntryDetailUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ServiceEntryDetail
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_error_delete_failed
import kotlinx.coroutines.Job
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
 * State holder for one entry's detail. Holds [ServiceLogDetailUiState], consumes
 * [ServiceLogDetailEvent]s, and emits one-shot [ServiceLogDetailEffect]s.
 *
 * Everything on screen comes from one observed read ([ObserveEntryDetailUseCase]) mapped by
 * [toUiState]; what is left here is the delete conversation — ask, confirm, write — and
 * turning the two bottom-bar actions into effects. The entry keeps flowing while the screen
 * is open, so a delete or an edit made elsewhere lands here without a refresh.
 */
internal class ServiceLogDetailViewModel(
    private val carId: CarId,
    private val logId: ServiceLogId,
    private val observeDetail: ObserveEntryDetailUseCase,
    private val deleteLog: DeleteServiceLogUseCase,
    private val telemetry: ServiceLogTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ServiceLogDetailUiState())
    val state: StateFlow<ServiceLogDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<ServiceLogDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ServiceLogDetailEffect> = _effects.receiveAsFlow()

    /** The in-flight delete, held so a second confirm can't write twice. */
    private var deleteJob: Job? = null

    /** Whether the entry has been seen once — so it is reported opened, not re-opened. */
    private var reportedOpen = false

    init {
        observeEntry()
    }

    private fun observeEntry() {
        viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.ENTRY_LOAD)) {
            observeDetail(carId, logId)
                // An unhandled failure inside a `collect` cancels the ViewModel's scope, so
                // an unreadable DB would take the whole screen down. Caught and reported
                // instead; the screen holds whatever it was already showing, because
                // "no longer available" would be a claim about the entry, not about the read.
                .catch { cause -> telemetry.readFailed(ServiceLogTelemetry.Source.DETAIL, cause) }
                .collect { detail -> if (detail == null) showNotFound() else show(detail) }
        }
    }

    fun onEvent(event: ServiceLogDetailEvent) = when (event) {
        ServiceLogDetailEvent.ShareClicked -> emit(ServiceLogDetailEffect.OpenShareRecord)
        ServiceLogDetailEvent.ReportOverchargeClicked -> emit(ServiceLogDetailEffect.OpenReportOvercharge(logId))
        ServiceLogDetailEvent.EditClicked -> emit(ServiceLogDetailEffect.OpenEditForm(logId))
        is ServiceLogDetailEvent.Delete -> onDeleteEvent(event)
        ServiceLogDetailEvent.BackClicked -> emit(ServiceLogDetailEffect.NavigateBack)
    }

    private fun onDeleteEvent(event: ServiceLogDetailEvent.Delete) = when (event) {
        ServiceLogDetailEvent.Delete.Requested -> showDelete(DeleteUiState.Confirming)
        ServiceLogDetailEvent.Delete.Dismissed -> showDelete(DeleteUiState.Idle)
        ServiceLogDetailEvent.Delete.Confirmed -> delete()
    }

    /**
     * Remove the entry — a **soft** delete, so the row survives for history and resale
     * checks. The screen only leaves once the write has actually landed; a failure keeps
     * the owner here with the reason, because an entry that silently stays is worse than
     * one that visibly refused to go.
     */
    private fun delete() {
        if (deleteJob?.isActive == true) return
        showDelete(DeleteUiState.InFlight)
        deleteJob = viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.DELETE_ENTRY)) {
            telemetry.entryDelete(logId) { deleteLog(logId) }.fold(
                ifLeft = { showDelete(DeleteUiState.Failed(UiText(Res.string.sl_error_delete_failed))) },
                ifRight = { emit(ServiceLogDetailEffect.Deleted) },
            )
        }
    }

    /* ------------------------------ State writers ------------------------------ */

    private fun show(detail: ServiceEntryDetail) {
        val entry = detail.toUiState()
        reportOpenedOnce(entry)
        _state.update { it.copy(content = ServiceLogDetailUiState.Content.Loaded(entry)) }
    }

    /**
     * The entry has gone — deleted from here, or from another surface while this screen was
     * open. Either way there is nothing left to render, and the delete conversation (if one
     * was up) is over.
     */
    private fun showNotFound() {
        telemetry.entryMissing(logId)
        _state.update { it.copy(content = ServiceLogDetailUiState.Content.NotFound, delete = DeleteUiState.Idle) }
    }

    private fun showDelete(delete: DeleteUiState) = _state.update { it.copy(delete = delete) }

    /**
     * Report the open once, on the first emission. The entry re-emits on every edit and on
     * the car's whole feed changing, and counting those as opens would turn one visit into
     * however many writes happened while it was on screen.
     */
    private fun reportOpenedOnce(entry: ServiceEntryDetailUiState) {
        if (reportedOpen) return
        reportedOpen = true
        telemetry.entryOpened(
            verified = entry.verification == VerificationStatus.VERIFIED,
            flagged = entry.isOvercharged,
        )
    }

    private fun emit(effect: ServiceLogDetailEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
