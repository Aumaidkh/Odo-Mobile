package com.hopcape.odo.core.domain.settings.model

import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.shared.DistanceUnit

/**
 * How the owner wants the app to look, measure and get in touch.
 *
 * Device-scoped, not account-scoped: a theme belongs to the phone it is read on, so this
 * is deliberately not part of [OwnerProfile][com.hopcape.odo.core.domain.owner.model.OwnerProfile]
 * and does not sync. Nothing here changes a stored number — units are a display and entry
 * choice, and every reading stays in kilometres and paise.
 *
 * A plain data class with defaults, because there is nothing to validate: every field is
 * an enum or a boolean, and a device with no stored settings has [Default] rather than an
 * error.
 */
data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val largerText: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.Default,
    val fuelEfficiencyUnit: FuelEfficiencyUnit = FuelEfficiencyUnit.Default,
    val notifications: NotificationPreferences = NotificationPreferences(),
) {
    companion object {
        /** What a device has before anything is chosen. */
        val Default: AppSettings = AppSettings()
    }
}
