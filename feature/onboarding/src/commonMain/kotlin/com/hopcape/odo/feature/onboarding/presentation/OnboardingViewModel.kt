package com.hopcape.odo.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.NonEmptyList
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.onboarding.domain.usecase.CompleteOnboardingCommand
import com.hopcape.odo.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.SaveCarCommand
import com.hopcape.odo.feature.onboarding.domain.usecase.SaveCarUseCase
import com.hopcape.odo.feature.onboarding.domain.usecase.VehicleCatalogSnapshot
import com.hopcape.odo.feature.onboarding.presentation.state.CarDetailsState
import com.hopcape.odo.feature.onboarding.presentation.state.CarStepState
import com.hopcape.odo.feature.onboarding.presentation.state.CatalogOptions
import com.hopcape.odo.feature.onboarding.presentation.state.FormField
import com.hopcape.odo.feature.onboarding.presentation.state.Loadable
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingGoalOption
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingUiState
import com.hopcape.odo.feature.onboarding.presentation.state.PlateLookup
import com.hopcape.odo.feature.onboarding.presentation.state.PlateLookupError
import com.hopcape.odo.feature.onboarding.presentation.state.ProfileState
import com.hopcape.odo.feature.onboarding.presentation.state.RtoMatch
import com.hopcape.odo.feature.onboarding.presentation.state.StartDestination
import com.hopcape.odo.feature.onboarding.presentation.state.text
import com.hopcape.odo.feature.onboarding.presentation.state.toDomain
import com.hopcape.odo.feature.onboarding.presentation.state.toStartDestination
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_details_catalog_error_body
import com.hopcape.odo.feature.onboarding.resources.onb_profile_name_error_blank
import com.hopcape.odo.feature.onboarding.resources.onb_profile_name_error_long
import com.hopcape.odo.feature.onboarding.resources.onb_profile_name_error_short
import com.hopcape.odo.feature.onboarding.resources.onb_save_error
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
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.cancellation.CancellationException

/**
 * State holder for first-run setup. Holds [OnboardingUiState], consumes [OnboardingEvent]s,
 * and emits one-shot [OnboardingEffect]s.
 *
 * It owns the flow's shape — which step is showing, whether the car step is in manual mode,
 * when a plate is worth looking up — and nothing else: the copy lives in `strings.xml`, the
 * rules live in the domain, and navigation lives in the route host that collects [effects].
 * Nothing here imports a nav or Compose type, which is what makes the whole flow testable
 * with a plain `runTest`.
 *
 * Reads top-down: [onEvent] dispatches, then one section per step, then the flow's own
 * step/finish logic, then the state writers every section above is phrased in. The writers
 * exist so the sections read as decisions ("show this lookup", "select that make") rather than
 * as nested `copy` plumbing.
 *
 * **Each step persists its own answers on Continue** rather than the whole flow saving at the
 * end: the car is stored when the car step is answered, so the first-scan step has a real car
 * behind it and abandoning setup halfway still leaves the owner with their car. A save that
 * fails keeps the owner on the step — [advance] is the only thing that moves the flow, and it
 * moves nothing until the write succeeds.
 */
internal class OnboardingViewModel(
    private val loadCatalog: LoadVehicleCatalogUseCase,
    private val loadModels: LoadCarModelsUseCase,
    private val lookupPlate: LookupPlateUseCase,
    private val saveCar: SaveCarUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val currentOwner: CurrentOwnerProvider,
    private val sessionStatus: SessionStatusProvider,
    private val telemetry: OnboardingTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    /** The in-flight plate lookup, held so a newer plate cancels a stale answer. */
    private var lookupJob: Job? = null

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

    init {
        telemetry.started()
        loadCatalogOptions()
    }

    fun onEvent(event: OnboardingEvent) = when (event) {
        is OnboardingEvent.Car -> onCarEvent(event)
        is OnboardingEvent.Details -> onDetailsEvent(event)
        is OnboardingEvent.Profile -> onProfileEvent(event)
        is OnboardingEvent.Scan -> onScanEvent(event)
        is OnboardingEvent.OdometerChanged -> onOdometerChanged(event.km)
        OnboardingEvent.ContinueClicked -> advance()
        OnboardingEvent.BackClicked -> goBack()
    }

    /* ------------------------------ Step 2 · plate route ------------------------------ */

    private fun onCarEvent(event: OnboardingEvent.Car) = when (event) {
        is OnboardingEvent.Car.PlateChanged -> onPlateChanged(event.plate)
        OnboardingEvent.Car.LookupRetried -> restartLookup()
        OnboardingEvent.Car.MatchRejected -> onMatchRejected()
    }

    /**
     * Take the new plate and drop whatever the last one resolved to — a match must never
     * outlive the plate it was found for — then start looking the new one up.
     */
    private fun onPlateChanged(plate: String) {
        updateCar { it.copy(plate = it.plate.update(plate), lookup = PlateLookup.Idle) }
        restartLookup()
    }

    /**
     * "Not your car?" — hand the owner the manual form. The match is dropped but the form it
     * seeded is not: rejecting a car usually means one field was wrong, not all four.
     */
    private fun onMatchRejected() {
        telemetry.manualEntryChosen(hadMatch = _state.value.car.match != null)
        _state.update { it.copy(manualEntry = true, car = it.car.copy(lookup = PlateLookup.Idle)) }
    }

    /**
     * Look the current plate up, cancelling whatever was already in flight.
     *
     * The debounce is what makes this safe to call on every keystroke: a plate only becomes
     * "complete" on its last character, and the owner may well keep typing past it (a BH
     * series plate is longer than a state one), so waiting a moment before spending a round
     * trip costs nothing and saves several.
     */
    private fun restartLookup() {
        lookupJob?.cancel()
        val plate = _state.value.car.takeIf { it.isPlateLookupReady }?.plate?.text ?: return
        lookupJob = viewModelScope.launch(telemetry.op(OnboardingTelemetry.Trace.PLATE_LOOKUP)) {
            delay(LOOKUP_DEBOUNCE_MILLIS)
            resolvePlate(plate)
        }
    }

    /**
     * Ask the registry who owns [plate].
     *
     * No registry integration is in MVP scope, so today's adapter answers unavailable and the
     * owner is sent to manual entry. That is the honest outcome, not a gap: a guessed match
     * would silently become the car every fairness benchmark and health score is computed
     * against.
     */
    private suspend fun resolvePlate(plate: String) {
        showLookup(PlateLookup.Loading)
        telemetry.plateLookup { lookupPlate(plate) }
            .fold(ifLeft = ::onLookupFailed, ifRight = ::onCarFound)
    }

    private fun onLookupFailed(error: DomainError) =
        showLookup(PlateLookup.Failed(error.toLookupError()))

    /** A car came back: show it for confirmation, and seed the manual form from it. */
    private fun onCarFound(vehicle: RegisteredVehicle) {
        val match = vehicle.toRtoMatch()
        showLookup(PlateLookup.Found(match))
        prefillDetails(match)
    }

    /* ------------------------------ Prefill from a match ------------------------------ */

    /**
     * Seed the manual form from what the registry found, so "Not your car?" opens a form that
     * already describes that car — the owner corrects the one field that is wrong instead of
     * re-answering all four. (Rejecting a match is nearly always about a wrong trim.)
     *
     * TODO(persistence): the plate route saves [RtoMatch] directly, so reconciling registry
     *  naming against the catalog belongs with the save rather than only here.
     */
    private fun prefillDetails(match: RtoMatch) {
        prefillYearAndFuel(match)
        val make = catalogMakeFor(match) ?: return
        selectMake(make)
        prefillModelOnceListed(make, match)
    }

    /** Always safe to seed: a year is a number and a fuel is an enum, so neither can disagree
     * with the catalog. */
    private fun prefillYearAndFuel(match: RtoMatch) = updateDetails {
        it.copy(year = it.year.update(match.year), fuel = it.fuel.update(match.fuelType))
    }

    /**
     * The **catalog's** spelling of the matched make, or `null` if it doesn't list it.
     *
     * The registry and the catalog are two different naming authorities ("Maruti" vs "Maruti
     * Suzuki"), and a make that isn't a catalog entry would show in the field while being
     * absent from the picker behind it — and would then key this car's fairness benchmarks to
     * a bucket of its own. `null` also covers "the catalog hasn't loaded", where there is
     * nothing to check the name against and the field is better left to the owner.
     */
    private fun catalogMakeFor(match: RtoMatch): String? = _state.value.details.options
        ?.makes
        ?.firstOrNull { it.equals(match.make, ignoreCase = true) }

    /** The model can only be seeded from the list for [make], so it waits for that read. */
    private fun prefillModelOnceListed(make: String, match: RtoMatch) = loadModelsFor(make) { models ->
        showModels(models)
        selectModel(models.matching(match))
    }

    /* ------------------------------ Step 2 · manual route ------------------------------ */

    private fun onDetailsEvent(event: OnboardingEvent.Details) = when (event) {
        is OnboardingEvent.Details.MakeSelected -> onMakeSelected(event.make)
        is OnboardingEvent.Details.ModelSelected -> selectModel(event.model)
        is OnboardingEvent.Details.YearSelected -> updateDetails { it.copy(year = it.year.update(event.year)) }
        is OnboardingEvent.Details.FuelSelected -> updateDetails { it.copy(fuel = it.fuel.update(event.fuel)) }
        OnboardingEvent.Details.CatalogRetried -> loadCatalogOptions()
        OnboardingEvent.Details.TryAutoFillClicked -> exitManualEntry()
    }

    private fun onMakeSelected(make: String) {
        selectMake(make)
        loadModelsFor(make) { models -> showModels(models) }
    }

    /** Load the brands, years and fuels the manual form can't render without. */
    private fun loadCatalogOptions() {
        showCatalog(Loadable.Loading)
        viewModelScope.launch(telemetry.op(OnboardingTelemetry.Trace.CATALOG_LOAD)) {
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
        modelsJob = viewModelScope.launch(telemetry.op(OnboardingTelemetry.Trace.MODELS_LOAD)) {
            onLoaded(telemetry.modelsLoad(make) { readOrNull { loadModels(make) } ?: emptyList() })
        }
    }

    /* ------------------------------ Step 3 ------------------------------ */

    private fun onProfileEvent(event: OnboardingEvent.Profile) = when (event) {
        is OnboardingEvent.Profile.NameChanged -> updateProfile { it.copy(name = it.name.update(event.name)) }
        is OnboardingEvent.Profile.GoalSelected -> onGoalSelected(event.goal)
    }

    /**
     * The goal is worth an event of its own rather than only appearing on completion: it decides
     * the surface the owner lands on, so which goals people pick is what says whether those
     * surfaces are the right three.
     */
    private fun onGoalSelected(goal: OnboardingGoalOption) {
        telemetry.goalSelected(goal.toDomain())
        updateProfile { it.copy(goal = it.goal.update(goal)) }
    }

    /* ------------------------------ Step 4 ------------------------------ */

    private fun onScanEvent(event: OnboardingEvent.Scan) = when (event) {
        OnboardingEvent.Scan.ScanClicked -> {
            // Bills scanned per month is the product's North Star, so where a scan was launched
            // from is worth knowing — this is the first one an owner is ever offered.
            telemetry.firstScanClicked()
            emit(OnboardingEffect.OpenBillScanner)
        }

        OnboardingEvent.Scan.SkipClicked -> {
            telemetry.firstScanSkipped()
            finish()
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
        saveJob = viewModelScope.launch(telemetry.op(OnboardingTelemetry.Trace.STEP_SUBMIT)) {
            if (!persist(current)) return@launch
            telemetry.stepAdvanced(from = current.step)
            val next = current.step.next
            if (next == null) finish() else showStep(next)
        }
    }

    /**
     * Store whatever this step is responsible for, answering `false` to keep the owner where
     * they are. Each step owns its own write, so leaving halfway keeps what was already
     * answered instead of discarding the lot.
     */
    private suspend fun persist(state: OnboardingUiState): Boolean = when (state.step) {
        OnboardingStep.CAR -> saveCarStep(state)
        OnboardingStep.PROFILE -> saveProfile(state)
        // The scan step stores nothing itself — it hands off to the scanner or skips.
        OnboardingStep.FIRST_SCAN -> true
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
            ifRight = { car -> savedCarId = car.id; true },
        )
    }

    /** Save the owner's answers and stamp setup as finished. */
    private suspend fun saveProfile(state: OnboardingUiState): Boolean = telemetry.profileSave {
        completeOnboarding(
            CompleteOnboardingCommand(
                name = state.profile.name.text,
                goal = state.profile.goal.value?.toDomain(),
            ),
        )
    }.fold(
        ifLeft = { errors -> reportSaveFailure(errors); false },
        ifRight = { true },
    )

    /**
     * Put each failure where the owner can act on it: on the field that owns it when one
     * does, and as a one-shot message when none does.
     *
     * Both halves matter. A field error without the message would leave a storage failure
     * invisible; the message without the field errors would make the owner hunt for which
     * answer was wrong.
     */
    private fun reportSaveFailure(errors: NonEmptyList<DomainError>) {
        val unowned = errors.filterNot(::showFieldError)
        if (unowned.isNotEmpty()) emit(OnboardingEffect.SaveFailed(UiText(Res.string.onb_save_error)))
    }

    /**
     * Attach [error] to the field it belongs to, answering `false` when no field owns it —
     * a storage failure isn't any one answer's fault.
     *
     * Only the name has a rendered error slot today. The car step's own validation failures
     * can't reach here anyway: [OnboardingUiState.canContinue] already refuses to submit an
     * unanswered car, so anything arriving from that step is a storage problem.
     */
    private fun showFieldError(error: DomainError): Boolean = when (error) {
        DomainError.BlankOwnerName -> failName(Res.string.onb_profile_name_error_blank)
        is DomainError.OwnerNameTooShort -> failName(Res.string.onb_profile_name_error_short)
        is DomainError.OwnerNameTooLong -> failName(Res.string.onb_profile_name_error_long)
        else -> false
    }

    private fun failName(message: StringResource): Boolean {
        updateProfile { it.copy(name = it.name.fail(UiText(message))) }
        return true
    }

    /**
     * Back rewinds one step. Two exceptions: manual entry is a *mode* of the car step, so
     * back leaves the mode before it leaves the step; and back from the first step leaves
     * the flow entirely, which only the route host can do.
     */
    private fun goBack() {
        val current = _state.value
        if (current.step == OnboardingStep.CAR && current.manualEntry) {
            exitManualEntry()
            return
        }
        val previous = current.step.previous
        if (previous == null) {
            telemetry.abandoned(current.step)
            emit(OnboardingEffect.NavigateBack)
        } else {
            telemetry.stepBack(current.step)
            showStep(previous)
        }
    }

    /**
     * Setup is over: the owner's goal picks the surface they land on, and sign-in is offered
     * only if there's no session — the ask can finally be concrete ("back up *these*
     * records") instead of an account wall on a blank app.
     */
    private fun finish() {
        val start = chosenStartDestination()
        val signInFirst = !sessionStatus.isSignedIn()
        telemetry.completed(
            goal = _state.value.profile.goal.value?.toDomain(),
            destination = start,
            signInOffered = signInFirst,
        )
        emit(OnboardingEffect.Finish(start = start, signInFirst = signInFirst))
    }

    /**
     * The surface the owner's goal earned them (PRD §5.1). A missing goal falls back to the
     * dashboard rather than blocking: the goal is a routing hint, and no hint is not a reason
     * to strand someone at the end of setup.
     */
    private fun chosenStartDestination(): StartDestination = _state.value.profile.goal.value
        ?.toDomain()
        ?.toStartDestination()
        ?: StartDestination.DASHBOARD

    /* ------------------------------ State writers ------------------------------ */

    private fun onOdometerChanged(km: Long) =
        _state.update { it.copy(odometer = it.odometer.update(km)) }

    private fun showStep(step: OnboardingStep) = _state.update { it.copy(step = step) }

    private fun exitManualEntry() = _state.update { it.copy(manualEntry = false) }

    private fun showLookup(lookup: PlateLookup) = updateCar { it.copy(lookup = lookup) }

    private fun showCatalog(catalog: Loadable<CatalogOptions>) =
        updateDetails { it.copy(catalog = catalog) }

    private fun showModels(models: List<CarModel>) = updateDetails { it.copy(models = models) }

    /** Take a make, dropping the model chosen under the previous one and the list it came from. */
    private fun selectMake(make: String) = updateDetails {
        it.copy(make = it.make.update(make), model = FormField(), models = emptyList())
    }

    /** `null` clears the choice — that is what an unlistable model resolves to. */
    private fun selectModel(model: CarModel?) = updateDetails { it.copy(model = it.model.update(model)) }

    private fun updateCar(transform: (CarStepState) -> CarStepState) =
        _state.update { it.copy(car = transform(it.car)) }

    private fun updateDetails(transform: (CarDetailsState) -> CarDetailsState) =
        _state.update { it.copy(details = transform(it.details)) }

    private fun updateProfile(transform: (ProfileState) -> ProfileState) =
        _state.update { it.copy(profile = transform(it.profile)) }

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

    private companion object {
        /** How long a plate must stop changing before it is worth a round trip. */
        const val LOOKUP_DEBOUNCE_MILLIS = 350L
    }
}

/* ------------------------------ Domain → state mappers ------------------------------ */

/**
 * Lookup failure → the copy the owner sees. Only the three lookup errors mean anything on
 * this screen; anything else reaching here is unexpected, and "couldn't check right now"
 * (retryable) is the truthful thing to say about an unexpected failure — unlike "no record
 * for this plate", which would be a claim about the registry we can't support.
 */
private fun DomainError.toLookupError(): PlateLookupError = when (this) {
    DomainError.RegistrationNotFound -> PlateLookupError.NOT_FOUND
    DomainError.LookupOffline -> PlateLookupError.OFFLINE
    else -> PlateLookupError.SERVICE
}

/**
 * The catalog entry for a matched car: the exact model **and** trim if the catalog lists it,
 * otherwise the same model without a trim, otherwise nothing.
 *
 * Falling back to the trim-less entry rather than the nearest trim is the point. Trim ladders
 * change with every facelift, so a seeded list is always incomplete — and a *wrong* trim is
 * worse than no trim, because it silently feeds ₹/km and every fairness benchmark.
 */
private fun List<CarModel>.matching(match: RtoMatch): CarModel? =
    firstOrNull { it.isSameModelAs(match) && it.variant.equals(match.variant, ignoreCase = true) }
        ?: firstOrNull { it.isSameModelAs(match) && it.variant == null }

private fun CarModel.isSameModelAs(match: RtoMatch): Boolean = name.equals(match.model, ignoreCase = true)

/**
 * The answered car step → the command that stores it.
 *
 * The two routes are two different authorities and this is the seam between them: manual
 * entry is answered against the catalog, while the plate route saves what the registry
 * claimed. The plate is kept either way — it is what a bill, a reminder or a resale report
 * will identify this car by — and the car is primary because setup only ever names the
 * first one.
 */
private fun OnboardingUiState.toSaveCarCommand(): SaveCarCommand {
    val model = details.model.value
    return SaveCarCommand(
        make = if (manualEntry) details.make.value else car.match?.make,
        model = if (manualEntry) model?.name else car.match?.model,
        variant = if (manualEntry) model?.variant else car.match?.variant,
        year = if (manualEntry) details.year.value else car.match?.year,
        fuelType = if (manualEntry) details.fuel.value else car.match?.fuelType,
        odometerKm = odometer.value?.toInt(),
        registrationNumber = car.plate.text,
        isPrimary = true,
    )
}

/** The registry's claim → the confirmation card's content. */
private fun RegisteredVehicle.toRtoMatch(): RtoMatch = RtoMatch(
    make = make,
    model = model,
    variant = variant,
    year = year.value,
    fuelType = fuelType,
)

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
