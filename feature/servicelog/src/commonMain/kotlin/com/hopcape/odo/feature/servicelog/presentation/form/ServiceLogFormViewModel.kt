package com.hopcape.odo.feature.servicelog.presentation.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.servicelog.domain.usecase.AddServiceLogCommand
import com.hopcape.odo.feature.servicelog.domain.usecase.AddServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.DeleteServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.GetServiceLogUseCase
import com.hopcape.odo.feature.servicelog.domain.usecase.UpdateServiceLogCommand
import com.hopcape.odo.feature.servicelog.domain.usecase.UpdateServiceLogUseCase
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry
import com.hopcape.odo.feature.servicelog.presentation.parseAmountPaise
import com.hopcape.odo.feature.servicelog.presentation.parseOdometerKm
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_error_amount_negative
import com.hopcape.odo.feature.servicelog.resources.sl_error_date_future
import com.hopcape.odo.feature.servicelog.resources.sl_error_date_required
import com.hopcape.odo.feature.servicelog.resources.sl_error_notes_too_long
import com.hopcape.odo.feature.servicelog.resources.sl_error_odometer_negative
import com.hopcape.odo.feature.servicelog.resources.sl_error_odometer_regression
import com.hopcape.odo.feature.servicelog.resources.sl_error_odometer_required
import com.hopcape.odo.feature.servicelog.resources.sl_error_save_failed
import com.hopcape.odo.feature.servicelog.resources.sl_error_workshop_too_long
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * State holder for the add/edit form. New entries default the date to today; editing
 * prefills from the existing entry. Submits an Add or Update command and maps each
 * [DomainError] onto its field (mirroring onboarding's `withErrors`).
 */
internal class ServiceLogFormViewModel(
    private val addLog: AddServiceLogUseCase,
    private val updateLog: UpdateServiceLogUseCase,
    private val getLog: GetServiceLogUseCase,
    private val deleteLog: DeleteServiceLogUseCase,
    private val owner: CurrentOwnerProvider,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val telemetry: ServiceLogTelemetry,
    private val carId: CarId,
    private val editLogId: ServiceLogId?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ServiceLogFormUiState(isEditing = editLogId != null, isLoading = editLogId != null),
    )
    val state: StateFlow<ServiceLogFormUiState> = _state.asStateFlow()

    private val _effects = Channel<ServiceLogFormEffect>(Channel.BUFFERED)
    val effects: Flow<ServiceLogFormEffect> = _effects.receiveAsFlow()

    init {
        telemetry.formOpened(isEdit = editLogId != null)
        if (editLogId != null) loadExisting(editLogId) else _state.update { it.copy(date = it.date.update(today())) }
    }

    fun onEvent(event: ServiceLogFormEvent) {
        when (event) {
            is ServiceLogFormEvent.WorkshopChanged -> _state.update { it.copy(workshop = it.workshop.update(event.value)) }
            is ServiceLogFormEvent.DateChanged -> _state.update { it.copy(date = it.date.update(event.value)) }
            is ServiceLogFormEvent.OdometerChanged -> _state.update { it.copy(odometer = it.odometer.update(event.value)) }
            is ServiceLogFormEvent.NotesChanged -> _state.update { it.copy(notes = it.notes.update(event.value)) }
            is ServiceLogFormEvent.AmountChanged -> _state.update { it.copy(amount = it.amount.update(event.value)) }
            is ServiceLogFormEvent.CategoryToggled -> _state.update { it.copy(categories = it.categories.toggle(event.category)) }
            ServiceLogFormEvent.ScanClicked -> Unit // coming soon (M2)
            ServiceLogFormEvent.Save -> submit()
            ServiceLogFormEvent.DeleteClicked -> _state.update { it.copy(showDeleteConfirm = true) }
            ServiceLogFormEvent.DismissDelete -> _state.update { it.copy(showDeleteConfirm = false) }
            ServiceLogFormEvent.ConfirmDelete -> confirmDelete()
            ServiceLogFormEvent.Back -> emit(ServiceLogFormEffect.Back)
        }
    }

    private fun today() = clock.now().toLocalDateTime(timeZone).date

    private fun loadExisting(id: ServiceLogId) {
        viewModelScope.launch {
            val entry = getLog(id).first()
            _state.update {
                if (entry == null) {
                    it.copy(isLoading = false)
                } else {
                    it.copy(
                        isLoading = false,
                        workshop = it.workshop.update(entry.workshopName?.value ?: ""),
                        date = it.date.update(entry.serviceDate),
                        odometer = it.odometer.update(entry.odometer.km.toString()),
                        notes = it.notes.update(entry.notes?.value ?: ""),
                        amount = it.amount.update((entry.totalAmount.paise / 100).toString()),
                        categories = entry.categories,
                    )
                }
            }
        }
    }

    private fun submit() {
        val s = _state.value
        _state.update { it.clearErrors().copy(isSubmitting = true) }
        viewModelScope.launch {
            val ownerId = owner.currentOwnerId()
            val date = s.date.value
            val odometerKm = parseOdometerKm(s.odometer.value)
            val amountPaise = parseAmountPaise(s.amount.value)
            val workshop = s.workshop.value?.ifBlank { null }
            val notes = s.notes.value?.ifBlank { null }

            val result = if (editLogId == null) {
                addLog(
                    AddServiceLogCommand(date, odometerKm, amountPaise, workshop, notes, categories = s.categories),
                    carId,
                    ownerId,
                )
            } else {
                updateLog(
                    UpdateServiceLogCommand(
                        editLogId, carId, ownerId, date, odometerKm, amountPaise, workshop, notes,
                        categories = s.categories,
                    ),
                )
            }

            result.fold(
                ifLeft = ::onErrors,
                ifRight = { entry ->
                    telemetry.saved(
                        isEdit = editLogId != null,
                        verified = entry.verification == VerificationStatus.VERIFIED,
                    )
                    _effects.send(ServiceLogFormEffect.Saved)
                },
            )
        }
    }

    private fun onErrors(errors: NonEmptyList<DomainError>) {
        val persistence = errors.any { it is DomainError.PersistenceFailure || it is DomainError.CarNotFound }
        telemetry.saveFailed(
            if (persistence) ServiceLogTelemetry.REASON_PERSISTENCE else ServiceLogTelemetry.REASON_VALIDATION,
        )
        _state.update { current ->
            var s = current.copy(isSubmitting = false)
            errors.forEach { error ->
                s = when (error) {
                    DomainError.MissingOdometer ->
                        s.copy(odometer = s.odometer.fail(UiText(Res.string.sl_error_odometer_required)))
                    DomainError.NegativeOdometer ->
                        s.copy(odometer = s.odometer.fail(UiText(Res.string.sl_error_odometer_negative)))
                    is DomainError.OdometerRegression ->
                        s.copy(odometer = s.odometer.fail(UiText(Res.string.sl_error_odometer_regression, listOf(error.previousKm))))
                    DomainError.NegativeAmount ->
                        s.copy(amount = s.amount.fail(UiText(Res.string.sl_error_amount_negative)))
                    DomainError.MissingServiceDate ->
                        s.copy(date = s.date.fail(UiText(Res.string.sl_error_date_required)))
                    DomainError.ServiceDateInFuture ->
                        s.copy(date = s.date.fail(UiText(Res.string.sl_error_date_future)))
                    is DomainError.WorkshopNameTooLong ->
                        s.copy(workshop = s.workshop.fail(UiText(Res.string.sl_error_workshop_too_long)))
                    is DomainError.NotesTooLong ->
                        s.copy(notes = s.notes.fail(UiText(Res.string.sl_error_notes_too_long)))
                    else -> s.copy(submitError = UiText(Res.string.sl_error_save_failed))
                }
            }
            s
        }
    }

    private fun confirmDelete() {
        if (editLogId == null) return
        _state.update { it.copy(showDeleteConfirm = false, isSubmitting = true) }
        viewModelScope.launch {
            deleteLog(editLogId).fold(
                ifLeft = { _state.update { it.copy(isSubmitting = false) } },
                ifRight = {
                    telemetry.deleted()
                    _effects.send(ServiceLogFormEffect.Deleted)
                },
            )
        }
    }

    private fun emit(effect: ServiceLogFormEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
