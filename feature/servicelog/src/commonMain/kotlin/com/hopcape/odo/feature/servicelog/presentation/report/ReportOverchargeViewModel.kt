package com.hopcape.odo.feature.servicelog.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.fairness.model.OverchargeReason
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.servicelog.domain.usecase.GetServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ReportOverchargeUseCase
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.state.Submission
import com.hopcape.odo.feature.servicelog.presentation.state.workDone
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_error_report_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for "Report this overcharge". Holds [ReportOverchargeUiState], consumes
 * [ReportOverchargeEvent]s, and emits one-shot [ReportOverchargeEffect]s.
 *
 * Two responsibilities and no more: show the entry being reported, and collect the owner's
 * answer. What counts as an overcharge was decided when the entry was checked (the stored
 * verdict), and where the report goes is [ReportOverchargeUseCase]'s business.
 */
internal class ReportOverchargeViewModel(
    private val logId: ServiceLogId,
    private val getLog: GetServiceLogUseCase,
    private val submitReport: ReportOverchargeUseCase,
    private val telemetry: ServiceLogTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportOverchargeUiState())
    val state: StateFlow<ReportOverchargeUiState> = _state.asStateFlow()

    private val _effects = Channel<ReportOverchargeEffect>(Channel.BUFFERED)
    val effects: Flow<ReportOverchargeEffect> = _effects.receiveAsFlow()

    /** The in-flight submit, held so a double tap can't file the report twice. */
    private var submitJob: Job? = null

    init {
        loadEntry()
    }

    /**
     * Read the entry once. Not observed: the owner is filling a form about this service as
     * it was when they opened it, and re-reading the verdict underneath them would change
     * what they are reporting mid-sentence.
     */
    private fun loadEntry() {
        viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.ENTRY_LOAD)) {
            // A failed read reports as itself and then falls through to the absent-entry
            // branch: there is no screen state for "couldn't read", and a report filed
            // against an entry we never loaded would be a claim with nothing behind it.
            val entry = runCatching { getLog(logId).first() }
                .onFailure { telemetry.readFailed(ServiceLogTelemetry.Source.REPORT, it) }
                .getOrNull()
            if (entry == null) telemetry.entryMissing(logId)
            show(entry.toContent())
        }
    }

    fun onEvent(event: ReportOverchargeEvent) = when (event) {
        is ReportOverchargeEvent.ReasonSelected -> selectReason(event.reason)
        is ReportOverchargeEvent.NoteChanged -> _state.update { it.copy(note = event.note) }
        ReportOverchargeEvent.SubmitClicked -> submit()
        ReportOverchargeEvent.DoneClicked, ReportOverchargeEvent.BackClicked ->
            emit(ReportOverchargeEffect.NavigateBack)
    }

    /** Picking a reason clears a previous failure — the next attempt starts clean. */
    private fun selectReason(reason: OverchargeReason) =
        _state.update { it.copy(reason = reason, submission = Submission.Idle) }

    /**
     * File the report. Guarded by [ReportOverchargeUiState.canSubmit] even though the button
     * is disabled without it — the state is the authority on whether there is a report to
     * file, not the bar rendering it.
     */
    private fun submit() {
        val current = _state.value
        val reason = current.reason
        if (!current.canSubmit || reason == null || submitJob?.isActive == true) return

        showSubmission(Submission.InFlight)
        submitJob = viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.SUBMIT_REPORT)) {
            telemetry.reportSubmit(reason) { submitReport(current.toReport(logId, reason)) }.fold(
                ifLeft = { showSubmission(Submission.Failed(UiText(Res.string.sl_error_report_failed))) },
                ifRight = { showSubmission(Submission.Succeeded) },
            )
        }
    }

    private fun show(content: ReportOverchargeUiState.Content) = _state.update { it.copy(content = content) }

    private fun showSubmission(submission: Submission) = _state.update { it.copy(submission = submission) }

    private fun emit(effect: ReportOverchargeEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}

/* ------------------------------ Domain ⇄ state mappers ------------------------------ */

/**
 * The entry as the screen's load phase. A report needs both an entry *and* the verdict it is
 * about: without the entry there is nothing here, and without an overcharge there is nothing
 * to report.
 */
private fun ServiceLogEntry?.toContent(): ReportOverchargeUiState.Content {
    val entry = this ?: return ReportOverchargeUiState.Content.NotFound
    val overchargedBy = entry.fairness?.overchargedBy ?: return ReportOverchargeUiState.Content.NotFlagged
    return ReportOverchargeUiState.Content.Loaded(
        ReportHeaderUiState(
            workshopName = entry.workshopName?.value,
            amountOver = overchargedBy,
            workDone = entry.workDone(),
            serviceDate = entry.serviceDate,
        ),
    )
}

/**
 * The owner's answers as the domain report. The note travels only when they wrote one —
 * an empty string would be stored as a note that says nothing.
 */
private fun ReportOverchargeUiState.toReport(logId: ServiceLogId, reason: OverchargeReason) = OverchargeReport(
    logId = logId,
    reason = reason,
    note = note.trim().ifEmpty { null },
)
