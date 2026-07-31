package com.hopcape.odo.feature.garage.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.garage.presentation.state.FormField
import com.hopcape.odo.feature.garage.presentation.state.Submission

/** What the owner did on the add-car screen. */
internal sealed interface AddCarEvent {

    data class PlateChanged(val value: String) : AddCarEvent
    data class MakeSelected(val make: String) : AddCarEvent
    data class ModelSelected(val model: CarModel) : AddCarEvent
    data class YearSelected(val year: Int) : AddCarEvent
    data class FuelSelected(val fuel: FuelType) : AddCarEvent
    data class OdometerChanged(val km: Long?) : AddCarEvent
    data object AddTapped : AddCarEvent
    data object CloseTapped : AddCarEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface AddCarEffect {
    data object Added : AddCarEffect
    data object NavigateBack : AddCarEffect
}

/**
 * Display state for the add-car screen.
 *
 * [match] is what a plate lookup came back with. There is no registry behind the port in
 * the MVP, so it stays `null` and the manual fields are the path every owner actually
 * takes — which is why they are always on screen rather than hidden behind "wrong car?".
 */
@Immutable
internal data class AddCarUiState(
    val fields: CarFormFields = CarFormFields(),
    val options: CarFormOptions = CarFormOptions(),
    val odometer: FormField<Long> = FormField(),
    val match: RegisteredVehicle? = null,
    /** True while a plate lookup is in flight. */
    val isLookingUp: Boolean = false,
    val submission: Submission = Submission.Idle,
)
