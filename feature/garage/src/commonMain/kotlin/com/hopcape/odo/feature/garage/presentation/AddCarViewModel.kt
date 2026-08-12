package com.hopcape.odo.feature.garage.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.garage.domain.usecase.AddCarCommand
import com.hopcape.odo.feature.garage.domain.usecase.AddCarUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LookupPlateUseCase
import com.hopcape.odo.feature.garage.presentation.state.FormField
import com.hopcape.odo.feature.garage.presentation.state.Submission
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_field_odometer
import com.hopcape.odo.feature.garage.resources.gr_error_save_failed
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
 * State holder for the add-car screen.
 *
 * The plate is looked up as it is typed, but only once it is a whole registration number —
 * a lookup on "MH12" is a round trip with a guaranteed answer. A lookup that comes back
 * empty is not an error the owner has to act on: the manual fields are already there, and
 * the screen simply stays as it is.
 */
internal class AddCarViewModel(
    private val addCar: AddCarUseCase,
    private val loadCatalog: LoadVehicleCatalogUseCase,
    private val loadModels: LoadCarModelsUseCase,
    private val lookupPlate: LookupPlateUseCase,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(AddCarUiState())
    val state: StateFlow<AddCarUiState> = _state.asStateFlow()

    private val _effects = Channel<AddCarEffect>(Channel.BUFFERED)
    val effects: Flow<AddCarEffect> = _effects.receiveAsFlow()

    /** The lookup in flight, so a fast typist does not race two answers onto the screen. */
    private var lookupJob: Job? = null

    init {
        telemetry.carFormOpened(GarageTelemetry.Screen.ADD_CAR)
        viewModelScope.launch {
            val catalog = loadCatalog()
            _state.update { it.copy(options = it.options.withCatalog(catalog)) }
        }
    }

    fun onEvent(event: AddCarEvent) = when (event) {
        is AddCarEvent.PlateChanged -> onPlateChanged(event.value)
        is AddCarEvent.MakeSelected -> onMakeSelected(event.make)
        is AddCarEvent.ModelSelected -> update { it.copy(model = it.model.update(event.model)) }
        is AddCarEvent.YearSelected -> update { it.copy(year = it.year.update(event.year)) }
        is AddCarEvent.FuelSelected -> update { it.copy(fuel = it.fuel.update(event.fuel)) }
        is AddCarEvent.OdometerChanged ->
            _state.update { it.copy(odometer = it.odometer.update(event.km), submission = Submission.Idle) }

        AddCarEvent.AddTapped -> save()
        AddCarEvent.CloseTapped -> emit(AddCarEffect.NavigateBack)
    }

    private fun onPlateChanged(value: String) {
        update { it.copy(registration = it.registration.update(value)) }
        lookupJob?.cancel()
        // Only a whole plate is worth asking about; anything shorter is still being typed,
        // and a lookup on it is a round trip with a guaranteed answer.
        val plate = RegistrationNumber.of(value)
        if (plate == null || plate.value.length < WHOLE_PLATE_LENGTH) {
            _state.update { it.copy(match = null, isLookingUp = false) }
            return
        }
        lookupJob = viewModelScope.launch {
            _state.update { it.copy(isLookingUp = true) }
            lookupPlate(value).fold(
                ifLeft = { error ->
                    telemetry.plateLookedUp(found = false, error = error)
                    _state.update { it.copy(match = null, isLookingUp = false) }
                },
                ifRight = { vehicle ->
                    telemetry.plateLookedUp(found = true, error = null)
                    _state.update { it.copy(match = vehicle, isLookingUp = false).prefilledFrom(vehicle) }
                },
            )
        }
    }

    /** A different brand invalidates the model — clear it rather than leave a Hyundai trim under "Tata". */
    private fun onMakeSelected(make: String) {
        update { it.copy(make = it.make.update(make), model = FormField()) }
        viewModelScope.launch {
            val models = loadModels(make)
            _state.update { it.copy(options = it.options.copy(models = models)) }
        }
    }

    private fun save() {
        val fields = _state.value.fields
        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(GarageTelemetry.Trace.SAVE_CAR)) {
            telemetry.carSave(GarageTelemetry.Screen.ADD_CAR) {
                addCar(
                    AddCarCommand(
                        make = fields.make.value,
                        model = fields.model.value?.name,
                        year = fields.year.value,
                        fuelType = fields.fuel.value,
                        odometerKm = _state.value.odometer.value?.toInt(),
                        variant = fields.model.value?.variant,
                        registrationNumber = fields.registration.value,
                        nickname = fields.nickname.value,
                    ),
                )
            }.fold(
                ifLeft = { errors ->
                    _state.update { state ->
                        state.copy(
                            fields = state.fields.withErrors(errors),
                            odometer = state.odometer.failedOdometer(errors.any { it.isOdometer() }),
                            submission = Submission.Failed(UiText(Res.string.gr_error_save_failed)),
                        )
                    }
                },
                ifRight = {
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    emit(AddCarEffect.Added)
                },
            )
        }
    }

    /** Edit one field and drop any pending failure — the form is being corrected. */
    private fun update(change: (CarFormFields) -> CarFormFields) {
        _state.update { it.copy(fields = change(it.fields), submission = Submission.Idle) }
    }

    private fun emit(effect: AddCarEffect) {
        _effects.trySend(effect)
        Unit
    }

    private companion object {
        /**
         * The shortest an Indian plate gets once spaces are stripped — two letters for the
         * state, one or two digits for the district, up to two letters for the series and
         * four digits ("DL1CAB1234", "MH12AB1234"). The domain's RegistrationNumber only
         * normalizes, so where "still typing" ends is a decision this screen makes.
         */
        const val WHOLE_PLATE_LENGTH = 9
    }
}

/**
 * Fill the form from what the registry claims, so the owner confirms a car rather than
 * describing one. Nothing is overwritten silently — the fields are empty until a lookup
 * lands, and the owner can change every one of them afterwards.
 */
private fun AddCarUiState.prefilledFrom(vehicle: RegisteredVehicle): AddCarUiState = copy(
    fields = fields.copy(
        make = fields.make.update(vehicle.make),
        model = fields.model.update(CarModel(name = vehicle.model, variant = vehicle.variant)),
        year = fields.year.update(vehicle.year.value),
        fuel = fields.fuel.update(vehicle.fuelType),
    ),
)

/** The odometer has no field of its own in [CarFormFields] — it is asked for once, here. */
private fun DomainError.isOdometer(): Boolean =
    this == DomainError.MissingOdometer || this == DomainError.NegativeOdometer

private fun FormField<Long>.failedOdometer(failed: Boolean): FormField<Long> =
    if (failed) fail(UiText(Res.string.gr_error_field_odometer)) else this
