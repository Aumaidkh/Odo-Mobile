package com.hopcape.odo.feature.garage.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.Submission

/** What the owner did on the edit-car screen. */
internal sealed interface EditCarEvent {

    data class MakeSelected(val make: String) : EditCarEvent
    data class ModelSelected(val model: CarModel) : EditCarEvent
    data class YearSelected(val year: Int) : EditCarEvent
    data class FuelSelected(val fuel: FuelType) : EditCarEvent
    data class PlateChanged(val value: String) : EditCarEvent
    data class NicknameChanged(val value: String) : EditCarEvent
    data object SaveTapped : EditCarEvent
    data object CloseTapped : EditCarEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface EditCarEffect {
    data object Saved : EditCarEffect
    data object NavigateBack : EditCarEffect
}

/**
 * Display state for the edit-car screen.
 *
 * The stored car has to be read before the form can be filled, so [form] is [Loadable].
 * There is no odometer here — the reading moves through the update sheet only, and this
 * screen says so instead of offering a second door to it.
 */
@Immutable
internal data class EditCarUiState(
    val form: Loadable<CarFormFields> = Loadable.Loading,
    val options: CarFormOptions = CarFormOptions(),
    val submission: Submission = Submission.Idle,
)
