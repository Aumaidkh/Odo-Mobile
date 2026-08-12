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

/**
 * Display state for the sign-out sheet.
 *
 * [isSigningOut] is on screen for as long as the wipe takes, and both buttons are disabled
 * while it is true. Leaving the sheet mid-wipe would cancel it.
 */
@Immutable
internal data class SignOutUiState(val isSigningOut: Boolean = false)

/**
 * The sign-out sheet's one outcome, raised only after the session is gone and the local
 * copy has been wiped. The caller navigates on it rather than on the tap, because moving
 * first is what left the old flow signed in.
 */
internal sealed interface SignOutEffect {
    data object SignedOut : SignOutEffect
}
