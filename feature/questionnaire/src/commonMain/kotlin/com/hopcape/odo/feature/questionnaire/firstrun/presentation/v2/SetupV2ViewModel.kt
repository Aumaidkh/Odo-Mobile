package com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.platform.notification.EngagementNudge
import com.hopcape.odo.core.platform.notification.EngagementNudgeScheduler
import com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase.LoadCarModelsUseCase
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the v2 setup: Model, then Year and fuel, then "Save my car". The make
 * comes from the Welcome tile. The car is saved with the odometer pending.
 *
 * v1's `OnboardingViewModel` stays untouched for the kill switch; both share the use cases.
 */
internal class SetupV2ViewModel(
    make: String,
    private val loadCatalog: LoadVehicleCatalogUseCase,
    private val loadModels: LoadCarModelsUseCase,
    private val saveCar: SaveCarUseCase,
    private val reportUnlisted: ReportUnlistedVehicleUseCase,
    private val currentOwner: CurrentOwnerProvider,
    private val telemetry: SetupTelemetry,
    private val nudges: EngagementNudgeScheduler,
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
    }

    fun onEvent(event: SetupV2Event) {
        when (event) {
            is SetupV2Event.ModelChosen -> chooseModel(event.model)
            is SetupV2Event.YearChosen -> {
                fieldAnswered(SetupTelemetry.Field.YEAR)
                _state.update { it.copy(year = event.year) }
            }
            is SetupV2Event.FuelChosen -> {
                fieldAnswered(SetupTelemetry.Field.FUEL)
                _state.update { it.copy(fuel = event.fuel) }
            }
            SetupV2Event.SaveClicked -> save()
            SetupV2Event.CatalogRetried -> {
                telemetry.catalogRetried()
                load()
            }
            SetupV2Event.ChangeMakeClicked -> leave()
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
    private fun chooseModel(model: CarModel) {
        fieldAnswered(SetupTelemetry.Field.MODEL)
        telemetry.stepAdvanced(SetupTelemetry.Step.MODEL)
        _state.update { it.copy(model = model, step = SetupV2Step.YEAR_FUEL) }
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
            ) {
                saveCar(command = command, ownerId = currentOwner.currentOwnerId(), existing = savedCarId)
            }
            _state.update { it.copy(saving = false) }
            result.fold(
                ifLeft = { emit(SetupV2Effect.SaveFailed(UiText(Res.string.onb_save_error))) },
                ifRight = { car ->
                    savedCarId = car.id
                    reportIfUnlisted(current, command)
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
