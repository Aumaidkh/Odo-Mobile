package com.hopcape.odo.feature.garage.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.feature.garage.domain.usecase.CarDetailsCommand
import com.hopcape.odo.feature.garage.domain.usecase.LoadCarModelsUseCase
import com.hopcape.odo.feature.garage.domain.usecase.LoadVehicleCatalogUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ObserveGarageUseCase
import com.hopcape.odo.feature.garage.domain.usecase.ReportUnlistedVehicleUseCase
import com.hopcape.odo.feature.garage.domain.usecase.UpdateCarDetailsUseCase
import com.hopcape.odo.feature.garage.presentation.state.FormField
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.Submission
import com.hopcape.odo.feature.garage.presentation.state.valueOrNull
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_error_no_car
import com.hopcape.odo.feature.garage.resources.gr_error_save_failed
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
 * State holder for the edit-car screen.
 *
 * The form is filled from the stored car once, not kept in step with it: the owner is
 * editing these values, and a later emission overwriting what they typed would lose their
 * work.
 */
internal class EditCarViewModel(
    private val activeCar: ActiveCarProvider,
    private val observeGarage: ObserveGarageUseCase,
    private val updateDetails: UpdateCarDetailsUseCase,
    private val loadCatalog: LoadVehicleCatalogUseCase,
    private val loadModels: LoadCarModelsUseCase,
    private val reportUnlisted: ReportUnlistedVehicleUseCase,
    private val telemetry: GarageTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(EditCarUiState())
    val state: StateFlow<EditCarUiState> = _state.asStateFlow()

    private val _effects = Channel<EditCarEffect>(Channel.BUFFERED)
    val effects: Flow<EditCarEffect> = _effects.receiveAsFlow()

    private var carId: CarId? = null

    init {
        telemetry.carFormOpened(GarageTelemetry.Screen.EDIT_CAR)
        load()
    }

    fun onEvent(event: EditCarEvent) = when (event) {
        is EditCarEvent.MakeSelected -> onMakeSelected(event.make)
        is EditCarEvent.ModelSelected -> update { it.copy(model = it.model.update(event.model)) }
        is EditCarEvent.YearSelected -> update { it.copy(year = it.year.update(event.year)) }
        is EditCarEvent.FuelSelected -> update { it.copy(fuel = it.fuel.update(event.fuel)) }
        is EditCarEvent.PlateChanged -> update { it.copy(registration = it.registration.update(event.value)) }
        is EditCarEvent.NicknameChanged -> update { it.copy(nickname = it.nickname.update(event.value)) }
        EditCarEvent.SaveTapped -> save()
        EditCarEvent.CloseTapped -> emit(EditCarEffect.NavigateBack)
    }

    private fun load() {
        val id = activeCar.activeCarId.value
        if (id == null) {
            telemetry.noActiveCar(GarageTelemetry.Screen.EDIT_CAR)
            _state.update { it.copy(form = Loadable.Failed(UiText(Res.string.gr_error_no_car))) }
            return
        }
        carId = id

        viewModelScope.launch {
            val catalog = loadCatalog()
            _state.update { it.copy(options = it.options.withCatalog(catalog)) }

            val car = observeGarage(id).first().car
            if (car == null) {
                telemetry.noActiveCar(GarageTelemetry.Screen.EDIT_CAR)
                _state.update { it.copy(form = Loadable.Failed(UiText(Res.string.gr_error_no_car))) }
                return@launch
            }
            _state.update { it.copy(form = Loadable.Ready(car.toFields())) }
            // The model list depends on the brand the car already has.
            _state.update { it.copy(options = it.options.copy(models = loadModels(car.make))) }
        }
    }

    /** A different brand invalidates the model — clear it rather than leave a Hyundai trim under "Tata". */
    private fun onMakeSelected(make: String) {
        update { it.copy(make = it.make.update(make), model = FormField()) }
        viewModelScope.launch {
            _state.update { it.copy(options = it.options.copy(models = loadModels(make))) }
        }
    }

    private fun save() {
        val id = carId
        val fields = _state.value.form.valueOrNull
        if (id == null || fields == null) {
            _state.update { it.copy(submission = Submission.Failed(UiText(Res.string.gr_error_no_car))) }
            return
        }

        _state.update { it.copy(submission = Submission.InFlight) }
        viewModelScope.launch(telemetry.op(GarageTelemetry.Trace.SAVE_CAR)) {
            telemetry.carSave(GarageTelemetry.Screen.EDIT_CAR) {
                updateDetails(
                    carId = id,
                    command = CarDetailsCommand(
                        make = fields.make.value,
                        model = fields.model.value?.name,
                        year = fields.year.value,
                        fuelType = fields.fuel.value,
                        variant = fields.model.value?.variant,
                        registrationNumber = fields.registration.value,
                        nickname = fields.nickname.value,
                    ),
                )
            }.fold(
                ifLeft = { errors ->
                    _state.update { state ->
                        state.copy(
                            form = Loadable.Ready(fields.withErrors(errors)),
                            submission = Submission.Failed(UiText(Res.string.gr_error_save_failed)),
                        )
                    }
                },
                ifRight = {
                    _state.update { it.copy(submission = Submission.Succeeded) }
                    reportIfUnlisted(fields)
                    emit(EditCarEffect.Saved)
                },
            )
        }
    }

    /**
     * See `AddCarViewModel.reportIfUnlisted` — same inference, same fire-and-forget contract,
     * and the same reason this suspends inline instead of spawning a child
     * `viewModelScope.launch`: [save] navigates away right after this returns.
     */
    private suspend fun reportIfUnlisted(fields: CarFormFields) {
        val make = fields.make.value ?: return
        val model = fields.model.value ?: return
        val options = _state.value.options
        val knownMake = options.makes.any { it.equals(make, ignoreCase = true) }
        val knownModel = options.models.any {
            it.name.equals(model.name, ignoreCase = true) && it.variant == model.variant
        }
        if (knownMake && knownModel) return
        reportUnlisted(make, model.name, model.variant)
    }

    /** Edit one field and drop any pending failure — the form is being corrected. */
    private fun update(change: (CarFormFields) -> CarFormFields) {
        _state.update { state ->
            val fields = state.form.valueOrNull ?: return@update state
            state.copy(form = Loadable.Ready(change(fields)), submission = Submission.Idle)
        }
    }

    private fun emit(effect: EditCarEffect) {
        _effects.trySend(effect)
        Unit
    }
}

/** The stored car as the form's starting answers. */
private fun Car.toFields(): CarFormFields = CarFormFields(
    make = FormField(make),
    model = FormField(CarModel(name = model, variant = variant)),
    year = FormField(year.value),
    fuel = FormField(fuelType),
    registration = FormField(registrationNumber?.value),
    nickname = FormField(nickname),
)
