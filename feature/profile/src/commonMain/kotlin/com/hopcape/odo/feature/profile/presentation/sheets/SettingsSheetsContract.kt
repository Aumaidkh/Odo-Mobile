package com.hopcape.odo.feature.profile.presentation.sheets

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit

/** What the owner chose on the appearance sheet. */
internal sealed interface AppearanceEvent {

    data class ThemeChosen(val theme: ThemePreference) : AppearanceEvent

    data class LargerTextToggled(val enabled: Boolean) : AppearanceEvent
}

/**
 * Display state for the appearance sheet. Follows what is stored, so a failed write leaves
 * the choice where it actually is rather than where it was tapped.
 */
@Immutable
internal data class AppearanceUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val largerText: Boolean = false,
    val error: UiText? = null,
)

/** What the owner chose on the units sheet. */
internal sealed interface UnitsEvent {

    data class DistanceUnitChosen(val unit: DistanceUnit) : UnitsEvent

    data class FuelEfficiencyUnitChosen(val unit: FuelEfficiencyUnit) : UnitsEvent
}

/** Display state for the units sheet. */
@Immutable
internal data class UnitsUiState(
    val distanceUnit: DistanceUnit = DistanceUnit.Default,
    val fuelEfficiencyUnit: FuelEfficiencyUnit = FuelEfficiencyUnit.Default,
    val error: UiText? = null,
)
