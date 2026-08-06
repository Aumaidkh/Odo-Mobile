package com.hopcape.odo.feature.servicelog.presentation.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.EitherNel
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.displayValue
import com.hopcape.odo.feature.servicelog.domain.usecase.AddServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetCurrentOdometerUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.UpdateServiceLogUseCase
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.state.Submission
import com.hopcape.odo.feature.servicelog.presentation.state.text
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_error_save_failed
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
 * State holder for the add / edit service form. Holds [ServiceLogFormUiState], consumes
 * [ServiceLogFormEvent]s, and emits one-shot [ServiceLogFormEffect]s.
 *
 * It owns the form's shape — what has been typed, what is being written, where a failure
 * belongs — and none of the rules: text becomes a command in [toAddCommand] /
 * [toUpdateCommand], the rules are enforced by `ServiceLogEntry.create` and the odometer
 * timeline inside the use cases, and a failure is routed to a field by [toFieldError].
 *
 * Reads top-down: [onEvent] dispatches, then the field writers, then the save, then the
 * failure routing.
 */
internal class ServiceLogFormViewModel(
    private val carId: CarId,
    editLogId: ServiceLogId?,
    private val addLog: AddServiceLogUseCase,
    private val updateLog: UpdateServiceLogUseCase,
    private val getLog: GetServiceLogUseCase,
    private val currentOdometer: GetCurrentOdometerUseCase,
    private val currentOwner: CurrentOwnerProvider,
    private val settings: AppSettingsRepository,
    private val telemetry: ServiceLogTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ServiceLogFormUiState(
            mode = editLogId?.let(ServiceLogFormMode::Edit) ?: ServiceLogFormMode.Add,
            isPrefilling = editLogId != null,
        ),
    )
    val state: StateFlow<ServiceLogFormUiState> = _state.asStateFlow()

    private val _effects = Channel<ServiceLogFormEffect>(Channel.BUFFERED)
    val effects: Flow<ServiceLogFormEffect> = _effects.receiveAsFlow()

    /** The in-flight write, held so a double tap on Save can't store the entry twice. */
    private var saveJob: Job? = null

    /**
     * The entry being edited, as it was read. Kept because the form is a partial view of it:
     * the priced lines and the bill photo are not on screen and must survive the edit
     * untouched (see [toUpdateCommand]).
     */
    private var original: ServiceLogEntry? = null

    init {
        viewModelScope.launch {
            // The unit first: both prefills render the reading in it, and a unit that
            // changed underneath a typed number would silently change what it means.
            readDistanceUnit()
            if (editLogId != null) prefill(editLogId) else prefillCurrentOdometer()
        }
    }

    /**
     * The unit the odometer field is typed in, which is a profile setting rather than
     * anything this form decides. Read once: the owner is typing a number into it, and a
     * unit that changed underneath them would silently change what that number means.
     */
    private suspend fun readDistanceUnit() {
        val unit = settings.observe().first().distanceUnit
        _state.update { it.copy(odometerUnit = unit.toOdoUnit(), distanceUnit = unit) }
    }

    /**
     * Start a new entry's odometer field at the car's latest known reading, so the owner
     * adjusts an up-to-date number instead of recalling one. Skipped when the car has no
     * readings, and never over something already typed.
     */
    private suspend fun prefillCurrentOdometer() {
        val reading = currentOdometer(carId) ?: return
        _state.update { state ->
            if (state.odometer.text.isNotBlank()) state
            else state.copy(odometer = state.odometer.update(reading.displayValue(state.distanceUnit).toString()))
        }
    }

    /**
     * Read the entry once and lay it into the fields. Once — not observed: the owner is
     * typing over these values, and a later emission would overwrite what they typed.
     */
    private fun prefill(id: ServiceLogId) {
        viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.ENTRY_LOAD)) {
            // A failed read is reported and then treated as "no entry": the form has nothing
            // to overwrite either way, and leaving the owner on empty fields that would
            // silently create a *second* entry is the outcome worth preventing.
            val entry = runCatching { getLog(id).first() }
                .onFailure { telemetry.readFailed(ServiceLogTelemetry.Source.FORM, it) }
                .getOrNull()
            if (entry == null) {
                telemetry.entryMissing(id)
                emit(ServiceLogFormEffect.NavigateBack)
                return@launch
            }
            original = entry
            _state.update { it.prefilledFrom(entry) }
        }
    }

    fun onEvent(event: ServiceLogFormEvent) = when (event) {
        is ServiceLogFormEvent.Field -> onFieldEvent(event)
        ServiceLogFormEvent.ScanBillClicked -> {
            telemetry.scanBillClicked(ServiceLogTelemetry.Source.FORM)
            emit(ServiceLogFormEffect.OpenBillScanner)
        }

        ServiceLogFormEvent.AttachBillClicked -> emit(ServiceLogFormEffect.AttachBill)
        ServiceLogFormEvent.SaveClicked -> save()
        ServiceLogFormEvent.CloseClicked -> emit(ServiceLogFormEffect.NavigateBack)
    }

    /* ------------------------------ Fields ------------------------------ */

    private fun onFieldEvent(event: ServiceLogFormEvent.Field) = _state.update { state ->
        when (event) {
            is ServiceLogFormEvent.Field.WorkshopChanged -> state.copy(workshop = state.workshop.update(event.value))
            is ServiceLogFormEvent.Field.DateChanged -> state.copy(date = state.date.update(event.date))
            is ServiceLogFormEvent.Field.OdometerChanged -> state.copy(odometer = state.odometer.update(event.value))
            is ServiceLogFormEvent.Field.AmountChanged -> state.copy(amount = state.amount.update(event.value))
            is ServiceLogFormEvent.Field.NotesChanged -> state.copy(notes = state.notes.update(event.value))
            is ServiceLogFormEvent.Field.CategoryToggled -> state.copy(categories = state.categories.toggle(event.category))
        }
    }

    /* ------------------------------ Save ------------------------------ */

    /**
     * Store the entry, then leave. Guarded by [ServiceLogFormUiState.canSave] even though
     * the button is disabled without it — the state is the authority on whether the form is
     * submittable, not the bar that happens to be rendering it.
     */
    private fun save() {
        if (!_state.value.canSave || saveJob?.isActive == true) return
        val form = _state.value.clearErrors()
        _state.value = form.copy(submission = Submission.InFlight)

        saveJob = viewModelScope.launch(telemetry.op(ServiceLogTelemetry.Trace.SAVE_ENTRY)) {
            telemetry.entrySave(edit = form.isEditing, categories = form.categories) { form.write() }
                .fold(
                    ifLeft = ::showFailure,
                    ifRight = { entry ->
                        // Leave InFlight before navigating. The form is popped on Saved, so
                        // this is invisible in the normal case — but a submission stuck at
                        // InFlight is a Save button that never re-enables, and `canSave`
                        // reads it. Every other form in the app settles on Succeeded too.
                        _state.update { it.copy(submission = Submission.Succeeded) }
                        emit(ServiceLogFormEffect.Saved(entry.id))
                    },
                )
        }
    }

    /**
     * The write itself — an add or an edit of the entry this form was opened on. An edit
     * writes against the entry as it was **read**, not against the route's arguments, so
     * the row keeps the car and owner it already belonged to.
     */
    private suspend fun ServiceLogFormUiState.write(): EitherNel<DomainError, ServiceLogEntry> {
        val mode = mode
        if (mode is ServiceLogFormMode.Edit) {
            // `original` is unreachably null in practice: an edit can't be submitted before
            // its entry has been read, because the fields are empty until then and Save is
            // disabled without an odometer.
            val entry = original ?: return nonEmptyListOf(DomainError.ServiceLogNotFound).left()
            return updateLog(toUpdateCommand(mode.id, entry.carId, entry.ownerId, entry))
        }
        return addLog(command = toAddCommand(), carId = carId, ownerId = currentOwner.currentOwnerId())
    }

    /**
     * Put each failure where the owner can act on it: on the field that owns it when one
     * does, and as a form-level message when none does.
     *
     * Both halves matter. A field error alone would leave a storage failure invisible; the
     * message alone would make the owner hunt for which answer was wrong. When every failure
     * found a field, the form-level banner stays away rather than saying the same thing twice.
     */
    private fun showFailure(errors: NonEmptyList<DomainError>) {
        val fieldErrors = errors.mapNotNull(DomainError::toFieldError)
        val allOwned = fieldErrors.size == errors.size
        _state.update { state ->
            fieldErrors.fold(state) { form, error -> form.withError(error) }
                .copy(submission = if (allOwned) Submission.Idle else saveFailed())
        }
    }

    private fun saveFailed(): Submission = Submission.Failed(UiText(Res.string.sl_error_save_failed))

    private fun emit(effect: ServiceLogFormEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}

/* ------------------------------ State writers ------------------------------ */

private fun ServiceLogFormUiState.withError(error: ServiceLogFormFieldError): ServiceLogFormUiState =
    when (error.field) {
        ServiceLogFormField.WORKSHOP -> copy(workshop = workshop.fail(error.message))
        ServiceLogFormField.DATE -> copy(date = date.fail(error.message))
        ServiceLogFormField.ODOMETER -> copy(odometer = odometer.fail(error.message))
        ServiceLogFormField.AMOUNT -> copy(amount = amount.fail(error.message))
        ServiceLogFormField.NOTES -> copy(notes = notes.fail(error.message))
    }

/** The design system's unit for the same choice — the field is labelled from it. */
internal fun DistanceUnit.toOdoUnit(): OdoDistanceUnit =
    if (this == DistanceUnit.MILE) OdoDistanceUnit.MILES else OdoDistanceUnit.KM

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
