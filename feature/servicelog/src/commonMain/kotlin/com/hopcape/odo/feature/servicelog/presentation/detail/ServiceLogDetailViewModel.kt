package com.hopcape.odo.feature.servicelog.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.feature.servicelog.domain.usecase.AttachBillPhotoUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.DeleteServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.ObserveEntryDetailUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.RecordEntryFairnessUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.fairnessItems
import com.hopcape.odo.feature.servicelog.domain.usecase.ServiceEntryDetail
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_error_attach_failed
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
    private val attachBillPhoto: AttachBillPhotoUseCase,
    private val recordFairness: RecordEntryFairnessUseCase,
    private val telemetry: ServiceLogTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ServiceLogDetailUiState())
    val state: StateFlow<ServiceLogDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<ServiceLogDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ServiceLogDetailEffect> = _effects.receiveAsFlow()

    /** The in-flight delete, held so a second confirm can't write twice. */
    private var deleteJob: Job? = null

    /** The in-flight attach, for the same reason: one picked file, one write. */
    private var attachJob: Job? = null

    /**
     * The entry as the domain holds it, kept because two actions need it rather than its
     * display form: the fairness check benchmarks the entry's own lines, and re-reading the
     * repository to get back what was just emitted would be a round trip and a race.
     */
    private var entry: ServiceLogEntry? = null

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
        ServiceLogDetailEvent.AttachBillClicked -> emit(ServiceLogDetailEffect.PickBillPhoto)
        is ServiceLogDetailEvent.BillPicked -> attachBill(event.pickedRef)
        ServiceLogDetailEvent.CheckFairnessClicked -> openFairness()
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

    /**
     * Attach the picked bill, then take the fairness verdict it makes possible.
     *
     * Two steps, in this order, because they fail differently. The attach is a local write
     * and is what earns the entry its Verified badge; the check needs the city pool and may
     * find nothing, or nothing worth judging. A failed check therefore leaves the owner with
     * their verified bill rather than undoing it, which is why its result is not folded into
     * this screen's error state.
     */
    private fun attachBill(pickedRef: String) {
        if (attachJob?.isActive == true) return
        _state.update { it.copy(attach = AttachUiState.InFlight) }
        attachJob = viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.ATTACH_BILL)) {
            telemetry.billAttach(logId) { attachBillPhoto(logId, carId, pickedRef) }.fold(
                ifLeft = { _state.update { state -> state.copy(attach = AttachUiState.Failed(UiText(Res.string.sl_error_attach_failed))) } },
                ifRight = { attached ->
                    _state.update { state -> state.copy(attach = AttachUiState.Idle) }
                    recordVerdict(attached)
                },
            )
        }
    }

    /**
     * Freeze the fairness verdict on the entry. Best effort: a benchmark the app could not
     * reach is not a reason to tell the owner their bill failed to attach, and the check can
     * be asked for again from the same screen.
     */
    private suspend fun recordVerdict(attached: ServiceLogEntry) {
        recordFairness(attached).fold(
            ifLeft = { telemetry.fairnessRecordFailed(logId, it) },
            ifRight = { judged -> telemetry.fairnessRecorded(judged.fairness?.outcome?.let { it::class.simpleName }) },
        )
    }

    /**
     * Open the shared fairness report for this entry's lines.
     *
     * The verdict is **stored before the report is shown**. A check the owner asked for is a
     * check the app should remember: it is what puts the badge on the ledger card, what the
     * savings total counts, and what makes the overcharge reportable at all — the report form
     * refuses an entry that was never judged. Freezing it here is the same reason
     * `FairnessSnapshot` exists, and the report screen re-runs the same analysis over the same
     * benchmarks, so the two cannot disagree.
     *
     * Nothing happens when there is nothing comparable — an entry with no priced lines and
     * more than one category tag cannot be split into jobs, and a report built on a guess is
     * worse than no report.
     */
    private fun openFairness() {
        val current = entry ?: return
        val lines = current.fairnessItems() ?: return
        telemetry.fairnessOpened()
        viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.CHECK_FAIRNESS)) {
            // Best effort: a benchmark the app could not reach is no reason to withhold the
            // screen that would have told the owner so.
            recordVerdict(current)
            _effects.send(
                ServiceLogDetailEffect.OpenFairness(
                    id = logId,
                    lines = lines.map {
                        FairnessCheckLine(
                            label = it.label,
                            categoryName = it.category?.name,
                            amountPaise = it.amount.paise,
                        )
                    },
                ),
            )
        }
    }

    /* ------------------------------ State writers ------------------------------ */

    private fun show(detail: ServiceEntryDetail) {
        entry = detail.entry
        val ui = detail.toUiState()
        reportOpenedOnce(ui)
        _state.update { it.copy(content = ServiceLogDetailUiState.Content.Loaded(ui)) }
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
