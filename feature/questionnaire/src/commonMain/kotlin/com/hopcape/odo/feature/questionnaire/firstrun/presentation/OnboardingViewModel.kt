package com.hopcape.odo.feature.questionnaire.firstrun.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.platform.notification.EngagementNudge
import com.hopcape.odo.core.platform.notification.EngagementNudgeScheduler
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.CompleteOnboardingUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.RecordDeclaredServiceUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.ReportUnlistedVehicleUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.SaveCarCommand
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.SaveCarUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.VehicleCatalogSnapshot
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.CarDetailsState
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.CatalogOptions
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.FormField
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.Loadable
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.feature.questionnaire.QuestionRegistry
import com.hopcape.odo.feature.questionnaire.presentation.toggle
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingUiState
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.text
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.onb_details_catalog_error_body
import com.hopcape.odo.feature.questionnaire.resources.onb_save_error
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * State holder for first-run setup. Holds [OnboardingUiState], consumes [OnboardingEvent]s,
 * and emits one-shot [OnboardingEffect]s.
 *
 * It owns the flow's shape — which step is showing, and when a step may be left — and
 * nothing else: the copy lives in `strings.xml`, the
 * rules live in the domain, and navigation lives in the route host that collects [effects].
 * Nothing here imports a nav or Compose type, which is what makes the whole flow testable
 * with a plain `runTest`.
 *
 * Reads top-down: [onEvent] dispatches, then one section per step, then the flow's own
 * step/finish logic, then the state writers every section above is phrased in. The writers
 * exist so the sections read as decisions ("show this step", "select that make") rather than
 * as nested `copy` plumbing.
 *
 * **Each step persists its own answers on Continue** rather than the whole flow saving at the
 * end: the car is stored when the car step is answered, so the first-scan step has a real car
 * behind it and abandoning setup halfway still leaves the owner with their car. A save that
 * fails keeps the owner on the step — [advance] is the only thing that moves the flow, and it
 * moves nothing until the write succeeds.
 */
internal class OnboardingViewModel(
    private val questions: QuestionRegistry,
    private val answers: QuestionnaireRepository,
    private val loadCatalog: LoadVehicleCatalogUseCase,
    private val loadModels: LoadCarModelsUseCase,
    private val saveCar: SaveCarUseCase,
    private val reportUnlisted: ReportUnlistedVehicleUseCase,
    private val recordDeclaredService: RecordDeclaredServiceUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val currentOwner: CurrentOwnerProvider,
    private val sessionStatus: SessionStatusProvider,
    private val telemetry: SetupTelemetry,
    private val nudges: EngagementNudgeScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    /** The in-flight model read, held so a newer make cancels a stale list. */
    private var modelsJob: Job? = null

    /** The in-flight save, held so a double tap on Continue can't write twice. */
    private var saveJob: Job? = null

    /**
     * The car this flow has already stored, if any. Its presence is what makes a second pass
     * over the car step an edit instead of a duplicate car — the owner stepping back to fix a
     * trim must not end up with two cars in their garage.
     */
    private var savedCarId: CarId? = null

    /**
     * Whether this attempt has already been accounted for, by finishing or by leaving. Guards
     * the [onCleared] backstop from counting a second abandonment on top of a real ending.
     */
    private var flowEnded: Boolean = false

    init {
        telemetry.started()
        loadCatalogOptions()
    }

    fun onEvent(event: OnboardingEvent) = when (event) {
        is OnboardingEvent.Details -> onDetailsEvent(event)
        is OnboardingEvent.OdometerChanged -> onOdometerChanged(event.km)
        OnboardingEvent.ContinueClicked -> advance()
        OnboardingEvent.BackClicked -> goBack()
    }

    /* ------------------------------ Step 1 · the car ------------------------------ */

    private fun onDetailsEvent(event: OnboardingEvent.Details) = when (event) {
        is OnboardingEvent.Details.MakeSelected -> onMakeSelected(event.make)
        is OnboardingEvent.Details.ModelSelected -> selectModel(event.model)
        is OnboardingEvent.Details.YearSelected -> updateDetails { it.copy(year = it.year.update(event.year)) }
        is OnboardingEvent.Details.FuelSelected -> updateDetails { it.copy(fuel = it.fuel.update(event.fuel)) }
        OnboardingEvent.Details.CatalogRetried -> loadCatalogOptions()
    }

    private fun onMakeSelected(make: String) {
        selectMake(make)
        loadModelsFor(make) { models -> showModels(models) }
    }

    /** Load the brands, years and fuels the manual form can't render without. */
    private fun loadCatalogOptions() {
        showCatalog(Loadable.Loading)
        viewModelScope.launch(telemetry.op(SetupTelemetry.Trace.CATALOG_LOAD)) {
            val options = telemetry.catalogLoad { readOrNull { loadCatalog().toOptions() } }
            showCatalog(if (options == null) catalogUnavailable() else Loadable.Ready(options))
        }
    }

    /**
     * A catalog read that fails leaves the form with nothing to offer, so it says so and
     * offers a retry rather than presenting empty pickers as if the catalog itself were empty.
     */
    private fun catalogUnavailable(): Loadable.Failed =
        Loadable.Failed(UiText(Res.string.onb_details_catalog_error_body))

    /**
     * Fetch the models for [make] and hand them to [onLoaded], cancelling any read already in
     * flight — a newer make must not have its list overwritten by an older one arriving late.
     *
     * A failed read yields an empty list: the picker's own "no models match" copy already says
     * as much, and there is nothing else useful to show.
     */
    private fun loadModelsFor(make: String, onLoaded: (List<CarModel>) -> Unit) {
        modelsJob?.cancel()
        modelsJob = viewModelScope.launch(telemetry.op(SetupTelemetry.Trace.MODELS_LOAD)) {
            onLoaded(telemetry.modelsLoad(make) { readOrNull { loadModels(make) } ?: emptyList() })
        }
    }

    /* ------------------------------ Flow ------------------------------ */

    /**
     * Advance a step, or finish on the last one. Guarded by [OnboardingUiState.canContinue]
     * even though the button is disabled without it — the state is the authority on whether
     * a step is answered, not the chrome that happens to be rendering it.
     */
    private fun advance() {
        val current = _state.value
        if (!current.canContinue || saveJob?.isActive == true) return
        // Leaving the car step without a reading is still a skip worth counting; it is just
        // no longer its own button. Reported before the write so a failed save does not lose
        // the fact that the owner declined to give one.
        if (current.step == OnboardingStep.CAR && current.odometer.value == null) {
            telemetry.odometerSkipped()
        }
        saveJob = viewModelScope.launch(telemetry.op(SetupTelemetry.Trace.STEP_SUBMIT)) {
            if (!persist(current)) return@launch
            telemetry.stepAdvanced(from = current.step)
            val next = current.step.next
            if (next == null) {
                // A failed stamp does not hold the owner here: the car is already stored.
                telemetry.stamp { completeOnboarding() }
                finish()
            } else {
                showStep(next)
            }
        }
    }

    /**
     * Store whatever this step is responsible for, answering `false` to keep the owner where
     * they are. Each step owns its own write, so leaving halfway keeps what was already
     * answered instead of discarding the lot.
     */
    private suspend fun persist(state: OnboardingUiState): Boolean = when (state.step) {
        OnboardingStep.CAR -> saveCarStep(state)
    }


    /**
     * Save the car, remembering its id so a later pass over this step edits that car rather
     * than adding another. Which of the two it is, is [SaveCarUseCase]'s business.
     */
    private suspend fun saveCarStep(state: OnboardingUiState): Boolean {
        val command = state.toSaveCarCommand()
        // Make and fuel are read off the command rather than the state: it is the one place
        // that resolves which route answered the step, and telemetry must report what was
        // actually stored.
        return telemetry.carSave(
            edit = savedCarId != null,
            make = command.make,
            fuel = command.fuelType?.name,
        ) {
            saveCar(command = command, ownerId = currentOwner.currentOwnerId(), existing = savedCarId)
        }.fold(
            ifLeft = { errors -> reportSaveFailure(errors); false },
            ifRight = { car ->
                savedCarId = car.id
                reportIfUnlisted(state, command)
                true
            },
        )
    }

    /**
     * The answers are compared against the catalog
     * come from the vehicle registry, which this catalog has no opinion on, so there is
     * nothing to compare there. See `:feature:garage`'s `AddCarViewModel.reportIfUnlisted` for
     * the same inference on the other place a car gets saved.
     *
     * Suspends inline rather than spawning a child `viewModelScope.launch` — [saveCarStep]'s
     * caller advances the step (and can finish the flow) right after this returns, which
     * would otherwise race a fire-and-forget launch against the ViewModel being cleared.
     */
    private suspend fun reportIfUnlisted(state: OnboardingUiState, command: SaveCarCommand) {
        val make = command.make ?: return
        val model = command.model ?: return
        val options = state.details.options ?: return
        val knownMake = options.makes.any { it.equals(make, ignoreCase = true) }
        val knownModel = state.details.models.any {
            it.name.equals(model, ignoreCase = true) && it.variant == command.variant
        }
        if (knownMake && knownModel) return
        reportUnlisted(make, model, command.variant)
    }

    /** Tell the owner a write failed, unless a field on screen already says why. */
    private fun reportSaveFailure(errors: NonEmptyList<DomainError>) {
        val unowned = errors.filterNot(::showFieldError)
        if (unowned.isNotEmpty()) emit(OnboardingEffect.SaveFailed(UiText(Res.string.onb_save_error)))
    }

    /**
     * The car step has no rendered error slot, so nothing is claimed by a field.
     *
     * Its own validation failures cannot reach here anyway: [OnboardingUiState.canContinue]
     * refuses to submit an unanswered car, so whatever arrives is a storage problem and is
     * reported as a message rather than marked on an answer.
     */
    private fun showFieldError(error: DomainError): Boolean = false

    /**
     * Back leaves the flow — the car step is the first one setup owns, and only the route
     * host can pop past it.
     */
    private fun goBack() {
        endAbandoned(_state.value.step)
        emit(OnboardingEffect.NavigateBack)
    }

    /**
     * The car is named and setup is stamped finished. First run still moves on to the value
     * screen, which pays for those answers.
     */
    private fun finish() {
        flowEnded = true
        telemetry.completed()
        // First run leaves nothing on this device that will ever ring. The owner has a car
        // and a modelled score; a day later that score is theirs to improve, and this is the
        // only thing that says so to somebody who has closed the app.
        viewModelScope.launch { nudges.schedule(EngagementNudge.FIRST_SCORE) }
        emit(OnboardingEffect.ShowValue)
    }

    /* ------------------------------ Funnel accounting ------------------------------ */

    /**
     * Most people do not press back — they leave. Without this the funnel showed silence
     * where it should have shown an abandonment, so the step people give up on was invisible.
     *
     * `onCleared` covers leaving the flow and the host being destroyed. A process the system
     * kills outright runs nothing at all, and no hook can change that.
     */
    override fun onCleared() {
        if (!flowEnded) endAbandoned(_state.value.step)
        super.onCleared()
    }

    private fun endAbandoned(step: OnboardingStep) {
        flowEnded = true
        telemetry.abandoned(step)
    }

    /* ------------------------------ State writers ------------------------------ */

    private fun onOdometerChanged(km: Long) =
        _state.update { it.copy(odometer = it.odometer.update(km)) }

    private fun showStep(step: OnboardingStep) = _state.update { it.copy(step = step) }

    private fun showCatalog(catalog: Loadable<CatalogOptions>) =
        updateDetails { it.copy(catalog = catalog) }

    /**
     * Setup lists models without their trims.
     *
     * A trim ladder changes with every facelift, and picking one is a tap that answers a
     * question nothing in first run asks. It is taken at the first service log instead,
     * where it actually moves the labour rate.
     */
    private fun showModels(models: List<CarModel>) = updateDetails {
        it.copy(models = models.map { model -> CarModel(model.name) }.distinctBy(CarModel::name))
    }

    /** Take a make, dropping the model chosen under the previous one and the list it came from. */
    private fun selectMake(make: String) = updateDetails {
        it.copy(make = it.make.update(make), model = FormField(), models = emptyList())
    }

    /** `null` clears the choice — that is what an unlistable model resolves to. */
    private fun selectModel(model: CarModel?) = updateDetails { it.copy(model = it.model.update(model)) }

    private fun updateDetails(transform: (CarDetailsState) -> CarDetailsState) =
        _state.update { it.copy(details = transform(it.details)) }

    private fun emit(effect: OnboardingEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /**
     * Run a read that may fail, answering `null` instead of throwing, so each caller decides
     * what an unavailable read looks like on screen.
     *
     * Cancellation is rethrown: a read that a newer one cancelled must not report itself as a
     * failure, or a stale make would overwrite the list the owner is actually looking at.
     */
    private suspend fun <T> readOrNull(read: suspend () -> T): T? =
        try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

}

/* ------------------------------ Domain → state mappers ------------------------------ */

/**
 * The answered car step → the command that stores it.
 *
 * No registration number: setup no longer asks for one. The column is nullable and the car
 * is stored without it until a feature that needs it asks. Primary because setup only ever
 * names the first car.
 */
private fun OnboardingUiState.toSaveCarCommand(): SaveCarCommand {
    val model = details.model.value
    return SaveCarCommand(
        make = details.make.value,
        model = model?.name,
        variant = model?.variant,
        year = details.year.value,
        fuelType = details.fuel.value,
        odometerKm = odometer.value?.toInt(),
        // No reading given: the car is stored reading zero, flagged, and asked for again.
        odometerPending = odometer.value == null,
        isPrimary = true,
    )
}


/**
 * Catalog snapshot → the pickers' options. The years list becomes a range because that is
 * what the year wheel takes; an empty catalog falls back to a usable range rather than an
 * empty wheel.
 */
private fun VehicleCatalogSnapshot.toOptions(): CatalogOptions = CatalogOptions(
    makes = makes,
    popularMakes = popularMakes,
    years = if (years.isEmpty()) CatalogOptions.DEFAULT_YEARS else years.min()..years.max(),
    fuelTypes = fuelTypes,
)
