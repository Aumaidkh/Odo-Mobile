package com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.setup.SetupDraft
import com.hopcape.odo.core.domain.setup.SetupDraftRepository
import com.hopcape.odo.core.platform.notification.EngagementNudge
import com.hopcape.odo.core.platform.notification.EngagementNudgeScheduler
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadMakeLogoUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.ReportUnlistedVehicleUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.SaveCarCommand
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.SaveCarUseCase
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.SetupTelemetry
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.label
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.Loadable
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.valueOrNull
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.onb_details_catalog_error_body
import com.hopcape.odo.feature.questionnaire.resources.onb_save_error
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * State holder for the v2 setup: Model, then Year and fuel, then "Save my car". The make
 * comes from the Welcome tile. The car is saved with the odometer pending.
 *
 * v1's `OnboardingViewModel` stays untouched for the kill switch; both share the use cases.
 *
 * Every answer is kept in a [SetupDraft] so a returning owner can carry on; [resume] opens
 * from that draft. The draft is cleared when the car is saved.
 */
internal class SetupV2ViewModel(
    make: String,
    private val resume: Boolean,
    private val drafts: SetupDraftRepository,
    private val clock: Clock,
    private val loadCatalog: LoadVehicleCatalogUseCase,
    private val loadModels: LoadCarModelsUseCase,
    private val saveCar: SaveCarUseCase,
    private val reportUnlisted: ReportUnlistedVehicleUseCase,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: SetupTelemetry,
    private val nudges: EngagementNudgeScheduler,
    private val loadMakeLogo: LoadMakeLogoUseCase? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupV2UiState(make = make))
    val state: StateFlow<SetupV2UiState> = _state.asStateFlow()

    private val _effects = Channel<SetupV2Effect>(Channel.BUFFERED)
    val effects: Flow<SetupV2Effect> = _effects.receiveAsFlow()

    /** Set once the car is stored, so a second save after back from the value screen edits it. */
    private var savedCarId: CarId? = null
    private var saveJob: Job? = null
    private var flowEnded = false
    private val answeredFields = mutableSetOf<String>()

    init {
        telemetry.started()
        // The make was the Welcome tap, the first answer of setup.
        fieldAnswered(SetupTelemetry.Field.MAKE)
        load()
        loadMakeLogo?.let { logo ->
            viewModelScope.launch {
                val url = logo(make) ?: return@launch
                _state.update { it.copy(makeLogoUrl = url) }
            }
        }
        viewModelScope.launch {
            // Restored first, so a resume reports the step it opens on and not Model.
            if (resume) restoreDraft()
            _state.map { it.step }.distinctUntilChanged().collect { telemetry.stepShown(stepName(it)) }
        }
    }

    /** Opens on the first unanswered step with the earlier answers selected. */
    private suspend fun restoreDraft() {
        val draft = drafts.observe().first() ?: return
        if (!draft.make.equals(_state.value.make, ignoreCase = true)) return
        val model = draft.modelName?.let { CarModel(it, draft.modelVariant) }
        model?.let { answeredFields += SetupTelemetry.Field.MODEL }
        draft.year?.let { answeredFields += SetupTelemetry.Field.YEAR }
        draft.fuel?.let { answeredFields += SetupTelemetry.Field.FUEL }
        _state.update {
            it.copy(
                model = model,
                year = draft.year,
                fuel = draft.fuel,
                step = if (model == null) SetupV2Step.MODEL else SetupV2Step.YEAR_FUEL,
            )
        }
    }

    /** Keeps the answers so far, so killing the app mid-setup loses nothing. */
    private fun saveDraft() {
        val state = _state.value
        val draft = SetupDraft(
            make = state.make,
            modelName = state.model?.name,
            modelVariant = state.model?.variant,
            year = state.year,
            fuel = state.fuel,
            updatedAt = clock.now(),
        )
        // A failed write is logged by the repository; setup carries on without it.
        viewModelScope.launch { drafts.save(draft) }
    }

    fun onEvent(event: SetupV2Event) {
        when (event) {
            is SetupV2Event.ModelChosen -> chooseModel(event.model, event.fromSearch)
            SetupV2Event.ModelSearchOpened -> {
                telemetry.modelSearchOpened()
                _state.update { it.copy(searchOpen = true) }
            }
            SetupV2Event.ModelSearchDismissed -> {
                // The sheet also reports a close after a pick, when it is already shut.
                if (_state.value.searchOpen) telemetry.modelSearchDismissed()
                _state.update { it.copy(searchOpen = false) }
            }
            is SetupV2Event.YearChosen -> {
                val previous = _state.value.year
                fieldAnswered(SetupTelemetry.Field.YEAR)
                telemetry.yearChosen(
                    year = event.year,
                    source = if (event.fromOlder) SetupTelemetry.YearSource.OLDER else SetupTelemetry.YearSource.CHIP,
                    changed = previous != null && previous != event.year,
                )
                _state.update { it.copy(year = event.year, olderOpen = false) }
                saveDraft()
            }
            SetupV2Event.OlderYearsOpened -> {
                telemetry.olderYearsOpened()
                _state.update { it.copy(olderOpen = true) }
            }
            SetupV2Event.OlderYearsDismissed -> _state.update { it.copy(olderOpen = false) }
            is SetupV2Event.FuelChosen -> {
                val previous = _state.value.fuel
                fieldAnswered(SetupTelemetry.Field.FUEL)
                telemetry.fuelChosen(event.fuel.name, changed = previous != null && previous != event.fuel)
                _state.update { it.copy(fuel = event.fuel) }
                saveDraft()
            }
            SetupV2Event.SaveClicked -> save()
            SetupV2Event.CatalogRetried -> {
                telemetry.catalogRetried()
                load()
            }
            SetupV2Event.ChangeMakeClicked -> {
                telemetry.changeMakeClicked()
                leave()
            }
            SetupV2Event.BackClicked -> when (_state.value.step) {
                SetupV2Step.MODEL -> leave()
                SetupV2Step.YEAR_FUEL -> {
                    telemetry.stepBack(stepName(SetupV2Step.YEAR_FUEL))
                    _state.update { it.copy(step = SetupV2Step.MODEL) }
                }
            }
        }
    }

    /** A model tap is the answer and the advance: there is no Continue button. */
    private fun chooseModel(model: CarModel, fromSearch: Boolean) {
        val listed = _state.value.catalog.valueOrNull?.models.orEmpty().contains(model)
        fieldAnswered(SetupTelemetry.Field.MODEL)
        telemetry.modelChosen(
            when {
                !fromSearch -> SetupTelemetry.ModelSource.GRID
                listed -> SetupTelemetry.ModelSource.SEARCH
                else -> SetupTelemetry.ModelSource.TYPED
            },
        )
        telemetry.stepAdvanced(SetupTelemetry.Step.MODEL)
        _state.update { it.copy(model = model, step = SetupV2Step.YEAR_FUEL, searchOpen = false) }
        saveDraft()
    }

    private fun load() {
        _state.update { it.copy(catalog = Loadable.Loading) }
        viewModelScope.launch(telemetry.op(SetupTelemetry.Trace.CATALOG_LOAD)) {
            val make = _state.value.make
            val catalog = runCatchingCancellableSuspend {
                val snapshot = loadCatalog()
                SetupV2Catalog(
                    makes = snapshot.makes,
                    models = telemetry.modelsLoad(make) { loadModels(make) },
                    years = snapshot.years.sortedDescending(),
                    fuels = snapshot.fuelTypes,
                )
            }.getOrNull()
            if (catalog == null) telemetry.catalogLoadFailed()
            else if (catalog.makes.isEmpty()) telemetry.catalogEmpty()
            _state.update {
                it.copy(
                    catalog = catalog?.let { c -> Loadable.Ready(c) }
                        ?: Loadable.Failed(UiText(Res.string.onb_details_catalog_error_body)),
                )
            }
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave || saveJob?.isActive == true) return
        val command = current.toCommand()
        _state.update { it.copy(saving = true) }
        saveJob = viewModelScope.launch(telemetry.op(SetupTelemetry.Trace.STEP_SUBMIT)) {
            val result = telemetry.carSave(
                edit = savedCarId != null,
                make = command.make,
                fuel = command.fuelType?.name,
                step = SetupTelemetry.Step.YEAR_FUEL,
                resumed = resume,
            ) {
                saveCar(command = command, ownerId = currentOwner.currentOwnerId(), existing = savedCarId)
            }
            _state.update { it.copy(saving = false) }
            result.fold(
                ifLeft = { emit(SetupV2Effect.SaveFailed(UiText(Res.string.onb_save_error))) },
                ifRight = { car ->
                    savedCarId = car.id
                    reportIfUnlisted(current, command)
                    drafts.clear()
                    finish()
                },
            )
        }
    }

    /** A typed make or model has no peer group yet, so it is reported for the catalog. */
    private suspend fun reportIfUnlisted(state: SetupV2UiState, command: SaveCarCommand) {
        val make = command.make ?: return
        val model = command.model ?: return
        val catalog = state.catalog.valueOrNull ?: return
        val knownMake = catalog.makes.any { it.equals(make, ignoreCase = true) }
        val knownModel = catalog.models.any { it.name.equals(model, ignoreCase = true) && it.variant == command.variant }
        if (!knownMake || !knownModel) reportUnlisted(make, model, command.variant)
    }

    private fun finish() {
        flowEnded = true
        telemetry.stepAdvanced(SetupTelemetry.Step.YEAR_FUEL)
        telemetry.completed()
        viewModelScope.launch { nudges.schedule(EngagementNudge.FIRST_SCORE) }
        emit(SetupV2Effect.ShowValue)
    }

    private fun leave() {
        endAbandoned()
        emit(SetupV2Effect.NavigateBack)
    }

    override fun onCleared() {
        if (!flowEnded) endAbandoned()
        super.onCleared()
    }

    private fun endAbandoned() {
        if (flowEnded) return
        flowEnded = true
        val state = _state.value
        telemetry.abandoned(stepName(state.step), fields = state.fieldAnswers(), catalog = state.catalog.label())
    }

    private fun fieldAnswered(field: String) {
        if (answeredFields.add(field)) telemetry.fieldAnswered(stepName(_state.value.step), field)
    }

    private fun emit(effect: SetupV2Effect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}

private fun stepName(step: SetupV2Step): String = when (step) {
    SetupV2Step.MODEL -> SetupTelemetry.Step.MODEL
    SetupV2Step.YEAR_FUEL -> SetupTelemetry.Step.YEAR_FUEL
}

/** No odometer: v2 saves the car pending a reading, asked for later on Home. */
private fun SetupV2UiState.toCommand(): SaveCarCommand = SaveCarCommand(
    make = make,
    model = model?.name,
    variant = model?.variant,
    year = year,
    fuelType = fuel,
    odometerKm = null,
    odometerPending = true,
    isPrimary = true,
)

private fun SetupV2UiState.fieldAnswers(): List<Pair<String, Boolean>> = listOf(
    SetupTelemetry.Field.MAKE to true,
    SetupTelemetry.Field.MODEL to (model != null),
    SetupTelemetry.Field.YEAR to (year != null),
    SetupTelemetry.Field.FUEL to (fuel != null),
)
