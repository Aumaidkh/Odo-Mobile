package com.hopcape.odo.feature.costtracker.presentation.fuelrate

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit

/** What the owner did on the fuel-rate sheet. */
internal sealed interface FuelRateEvent {

    /** The price field changed. Rupees as typed; the ViewModel turns them into paise. */
    data class PriceChanged(val text: String) : FuelRateEvent

    data object SaveTapped : FuelRateEvent

    /** "Use Odo's estimate instead" — drops the rate they had set. */
    data object ClearTapped : FuelRateEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface FuelRateEffect {

    /** Saved or cleared — either way the sheet is done. */
    data object Dismiss : FuelRateEffect
}

/**
 * The sheet's state.
 *
 * [unit] decides the field's label (per litre, per kg, per unit), so it is read before the
 * sheet can say anything. [canClear] is true only when the owner has a rate of their own to
 * drop — offering to "use Odo's estimate" when that is already what is showing would be a
 * button that does nothing.
 */
@Immutable
internal data class FuelRateUiState(
    val unit: FuelUnit = FuelUnit.LITRE,
    val price: String = "",
    val error: UiText? = null,
    val canClear: Boolean = false,
    val saving: Boolean = false,
)
