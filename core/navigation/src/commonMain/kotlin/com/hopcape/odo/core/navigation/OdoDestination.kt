package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Every screen the app can navigate to, modelled as a typed Navigation 3 [NavKey].
 *
 * Centralised on purpose (the inspiration's model): a feature references these
 * shared destinations instead of importing another feature, which keeps the
 * `:feature:*` modules decoupled while still allowing cross-feature jumps. Because
 * the keys are typed, arguments are type-safe — `CarDetail(carId)` instead of a
 * stringly-typed `"car/{carId}"` template with manual `navArgument` parsing.
 *
 * To add a screen, add a subtype here; to make it appear in the bottom bar, make
 * it a [TopLevel].
 */
sealed interface OdoDestination : NavKey {

    /** Destinations shown as roots in the bottom navigation bar. */
    sealed interface TopLevel : OdoDestination {
        /** Short label rendered under the bottom-bar item. */
        val label: String
    }

    // --- Bottom-nav roots ---
    data object Home : TopLevel { override val label = "Home" }
    data object Garage : TopLevel { override val label = "Garage" }
    data object Reminders : TopLevel { override val label = "Reminders" }
    data object Profile : TopLevel { override val label = "Profile" }

    // --- Nested / argument-carrying destinations ---
    data class CarDetail(val carId: String) : OdoDestination

    // --- Service log (per car) ---
    /** The service-log list for a car. */
    data class ServiceLog(val carId: String) : OdoDestination
    /** A single service entry's detail screen. */
    data class ServiceLogDetail(val logId: String, val carId: String) : OdoDestination
    /** The add/edit form; [editLogId] non-null puts it in edit mode (same screen). */
    data class AddServiceLog(val carId: String, val editLogId: String? = null) : OdoDestination

    data object BillScanner : OdoDestination

    // --- Onboarding flow (first-run car setup) ---
    /** Intro carousel shown on first launch, before car setup. */
    data object Welcome : OdoDestination
    data object Onboarding : OdoDestination

    // --- Auth flow ---
    data object AuthLogin : OdoDestination
    data object AuthOtp : OdoDestination

    companion object {
        /** Ordered bottom-navigation roots. */
        val topLevel: List<TopLevel> = listOf(Home, Garage, Reminders, Profile)
    }
}
