package com.hopcape.odo.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.usecase.AddCarUseCase
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEffect
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEvent
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_error_fuel_required
import com.hopcape.odo.feature.onboarding.resources.onb_error_make_required
import com.hopcape.odo.feature.onboarding.resources.onb_error_model_required
import com.hopcape.odo.feature.onboarding.resources.onb_error_odometer_negative
import com.hopcape.odo.feature.onboarding.resources.onb_error_odometer_required
import com.hopcape.odo.feature.onboarding.resources.onb_error_save_failed
import com.hopcape.odo.feature.onboarding.resources.onb_error_year_range
import com.hopcape.odo.feature.onboarding.resources.onb_error_year_required
import com.hopcape.odo.feature.onboarding.resources.onb_scan_unavailable
import com.hopcape.odo.feature.onboarding.presentation.contract.StartDestination
import com.hopcape.odo.feature.onboarding.presentation.contract.toStartDestination
import com.hopcape.odo.feature.onboarding.presentation.scan.HistoryScanLauncher
import com.hopcape.odo.feature.onboarding.presentation.scan.HistoryScanOutcome
import com.hopcape.odo.feature.onboarding.presentation.state.CarForm
import com.hopcape.odo.feature.onboarding.presentation.state.FormField
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * State holder for the 3-screen car-onboarding flow. Holds [OnboardingUiState],
 * consumes [OnboardingEvent]s, and emits one-shot [OnboardingEffect]s.
 *
 * Validation rules live in the domain ([Car.create] / [AddCarUseCase]); this
 * ViewModel only invokes them at the right moments and maps [DomainError] to
 * field-level, Hinglish messages on [CarForm]. The routing decision is emitted as
 * data ([StartDestination]); the ViewModel never touches navigation or Compose types.
 *
 * All observability is delegated to [OnboardingTelemetry] behind intent-named calls
 * (`telemetry.started()`, `telemetry.completed(...)`), and each async op runs under
 * `telemetry.op(...)` so its span and logs correlate — so this file reads as the
 * flow's logic, not a wall of logging/analytics/tracing.
 */
internal class OnboardingViewModel(
    private val addCar: AddCarUseCase,
    private val catalog: VehicleCatalog,
    private val owner: CurrentOwnerProvider,
    private val ids: IdGenerator,
    private val scanLauncher: HistoryScanLauncher,
    private val telemetry: OnboardingTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    /** Tracks the in-flight model lookup so a newer make cancels a stale one. */
    private var modelsJob: Job? = null

    init {
        telemetry.started()
        loadCatalog()
    }

    /** Load the make/year/fuel reference data that the Step-1 dropdowns render. */
    private fun loadCatalog() {
        viewModelScope.launch(telemetry.op(OnboardingTelemetry.Trace.CATALOG_LOAD)) {
            val makes = telemetry.catalogLoaded { safeCatalog { catalog.makes() } }
            _state.update {
                it.copy(
                    isCatalogLoading = false,
                    makes = makes,
                    years = catalog.years(),
                    fuelTypes = catalog.fuelTypes(),
                )
            }
        }
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.MakeChanged -> onMakeChanged(event.value)
            is OnboardingEvent.ModelChanged ->
                updateForm { it.copy(model = it.model.update(event.value)) }
            is OnboardingEvent.YearChanged ->
                updateForm { it.copy(year = it.year.update(event.value)) }
            is OnboardingEvent.FuelTypeChanged ->
                updateForm { it.copy(fuelType = it.fuelType.update(event.value)) }
            is OnboardingEvent.OdometerChanged ->
                updateForm { it.copy(odometer = it.odometer.update(event.value)) }
            is OnboardingEvent.PurchaseYearChanged ->
                updateForm { it.copy(purchaseYear = it.purchaseYear.update(event.value)) }
            is OnboardingEvent.VariantChanged ->
                updateForm { it.copy(variant = it.variant.update(event.value)) }
            is OnboardingEvent.RegistrationChanged ->
                updateForm { it.copy(registrationNumber = it.registrationNumber.update(event.value)) }
            is OnboardingEvent.NicknameChanged ->
                updateForm { it.copy(nickname = it.nickname.update(event.value)) }

            OnboardingEvent.CarDetailsNext -> validateAndAdvance()
            OnboardingEvent.Back -> onBack()

            OnboardingEvent.ScanHistoryClicked -> onScanClicked()
            OnboardingEvent.SkipHistory -> onSkipHistory()

            is OnboardingEvent.GoalSelected -> onGoalSelected(event.goal)
            OnboardingEvent.Finish -> _state.value.selectedGoal?.let(::submit)
        }
    }

    private fun updateForm(transform: (CarForm) -> CarForm) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    /**
     * Back from the first step ([OnboardingStep.CAR_DETAILS]) exits onboarding —
     * emitted as [OnboardingEffect.NavigateBack] so the nav host pops back to the
     * intro carousel. From any later step it just rewinds one step.
     */
    private fun onBack() {
        val step = _state.value.step
        if (step == OnboardingStep.CAR_DETAILS) {
            telemetry.abandoned(step)
            viewModelScope.launch { _effects.send(OnboardingEffect.NavigateBack) }
        } else {
            telemetry.stepBack(step)
            _state.update { it.copy(step = step.previous(), scanUnavailableMessage = null) }
        }
    }

    private fun onMakeChanged(make: String) {
        // A new make invalidates the previously chosen model and its option list.
        modelsJob?.cancel()
        _state.update {
            it.copy(form = it.form.copy(make = it.form.make.update(make), model = FormField()), models = emptyList())
        }
        modelsJob = viewModelScope.launch(telemetry.op(OnboardingTelemetry.Trace.MODELS_LOAD)) {
            val models = telemetry.modelsLoaded(make) { safeCatalog { catalog.models(make) } }
            _state.update { it.copy(models = models) }
        }
    }

    /** Step-1 gate: validate via the domain factory (no persistence) before advancing. */
    private fun validateAndAdvance() {
        val form = _state.value.form
        Car.create(
            id = CarId.new(ids),
            ownerId = owner.currentOwnerId(),
            make = form.make.value,
            model = form.model.value,
            year = form.year.value,
            fuelType = form.fuelType.value,
            odometerKm = form.odometer.value?.trim()?.toIntOrNull(),
            variant = form.variant.value?.ifBlank { null },
            registrationNumber = form.registrationNumber.value?.ifBlank { null },
            purchaseYear = form.purchaseYear.value,
            nickname = form.nickname.value?.ifBlank { null },
            isPrimary = true,
        ).fold(
            ifLeft = { errors ->
                _state.update { it.withErrors(errors) }
                telemetry.carDetailsInvalid(errors.errorTypes(), errors.size)
            },
            ifRight = {
                _state.update {
                    it.copy(form = it.form.clearErrors(), submitError = null, step = OnboardingStep.HISTORY_IMPORT)
                }
                telemetry.carDetailsSubmitted(form.make.value, form.fuelType.value?.name)
            },
        )
    }

    private fun onSkipHistory() {
        telemetry.historySkipped()
        _state.update { it.copy(step = OnboardingStep.GOAL_SELECTION, scanUnavailableMessage = null) }
    }

    private fun onGoalSelected(goal: OnboardingGoal) {
        telemetry.goalSelected(goal)
        _state.update { it.copy(selectedGoal = goal, submitError = null) }
    }

    private fun onScanClicked() {
        telemetry.scanClicked()
        viewModelScope.launch(telemetry.op(OnboardingTelemetry.Trace.SCAN)) {
            when (scanLauncher.scan()) {
                HistoryScanOutcome.NotAvailable -> {
                    _state.update { it.copy(scanUnavailableMessage = UiText(Res.string.onb_scan_unavailable)) }
                    telemetry.scanUnavailable()
                }
            }
        }
    }

    private fun submit(goal: OnboardingGoal) {
        _state.update { it.copy(selectedGoal = goal, isSubmitting = true, submitError = null) }
        viewModelScope.launch(telemetry.op(OnboardingTelemetry.Trace.ADD_CAR)) {
            val form = _state.value.form
            telemetry.addCarWrite(goal) { addCar(form.toCommand(), owner.currentOwnerId()) }
                .fold(
                    ifLeft = { errors -> onSaveFailed(errors, goal) },
                    ifRight = { car -> onCarSaved(car, goal) },
                )
        }
    }

    private suspend fun onCarSaved(car: Car, goal: OnboardingGoal) {
        _state.update { it.copy(isSubmitting = false) }
        val destination = goal.toStartDestination()
        telemetry.completed(goal, destination, car.id.value)
        _effects.send(OnboardingEffect.NavigateToStart(destination, car.id.value))
    }

    private suspend fun onSaveFailed(errors: NonEmptyList<DomainError>, goal: OnboardingGoal) {
        val onlyPersistence = errors.all { it is DomainError.PersistenceFailure }
        _state.update {
            val applied = it.withErrors(errors).copy(isSubmitting = false)
            // Validation failures send the user back to fix the form; a pure
            // persistence failure keeps them on Goal Selection to retry.
            if (onlyPersistence) applied else applied.copy(step = OnboardingStep.CAR_DETAILS)
        }
        telemetry.failed(goal, if (onlyPersistence) "persistence" else "validation", errors.errorTypes())
    }

    /**
     * Run a catalog read that may hit the DB, returning an empty list on failure so
     * the UI degrades gracefully. Cancellation is rethrown so a stale lookup that
     * a newer make cancelled does not write back a misleading empty result.
     */
    private suspend fun safeCatalog(read: suspend () -> List<String>): List<String> =
        try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            telemetry.catalogReadFailed(e.message ?: e::class.simpleName)
            emptyList()
        }
}

/**
 * A comma-joined list of the accumulated errors' *type* names — safe to emit as
 * telemetry (no user input, no PII), enough for a dashboard to see which rules failed.
 */
private fun NonEmptyList<DomainError>.errorTypes(): String =
    joinToString(",") { it::class.simpleName ?: "Unknown" }

/** Previous step, clamped at the first. */
private fun OnboardingStep.previous(): OnboardingStep = when (this) {
    OnboardingStep.CAR_DETAILS -> OnboardingStep.CAR_DETAILS
    OnboardingStep.HISTORY_IMPORT -> OnboardingStep.CAR_DETAILS
    OnboardingStep.GOAL_SELECTION -> OnboardingStep.HISTORY_IMPORT
}

/**
 * Map accumulated [DomainError]s onto the state: each validation failure attaches to
 * its [CarForm] field; a persistence failure becomes the general [OnboardingUiState.submitError].
 * Pure (no ViewModel state) so it is independently testable.
 */
private fun OnboardingUiState.withErrors(errors: NonEmptyList<DomainError>): OnboardingUiState {
    var form = form.clearErrors()
    var submitError: UiText? = null
    errors.forEach { error ->
        when (error) {
            DomainError.MissingOdometer ->
                form = form.copy(odometer = form.odometer.fail(UiText(Res.string.onb_error_odometer_required)))
            DomainError.NegativeOdometer ->
                form = form.copy(odometer = form.odometer.fail(UiText(Res.string.onb_error_odometer_negative)))
            DomainError.MissingYear ->
                form = form.copy(year = form.year.fail(UiText(Res.string.onb_error_year_required)))
            is DomainError.YearOutOfRange -> {
                val message = UiText(
                    id = Res.string.onb_error_year_range,
                    args = listOf(ModelYear.RANGE.first, ModelYear.RANGE.last),
                )
                form = if (error.field == "purchaseYear") {
                    form.copy(purchaseYear = form.purchaseYear.fail(message))
                } else {
                    form.copy(year = form.year.fail(message))
                }
            }
            DomainError.MissingFuelType ->
                form = form.copy(fuelType = form.fuelType.fail(UiText(Res.string.onb_error_fuel_required)))
            DomainError.BlankMake ->
                form = form.copy(make = form.make.fail(UiText(Res.string.onb_error_make_required)))
            DomainError.BlankModel ->
                form = form.copy(model = form.model.fail(UiText(Res.string.onb_error_model_required)))
            is DomainError.PersistenceFailure ->
                submitError = UiText(Res.string.onb_error_save_failed)
        }
    }
    return copy(form = form, submitError = submitError)
}
