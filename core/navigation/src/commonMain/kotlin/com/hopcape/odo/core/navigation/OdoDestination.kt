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

    // --- Service log (per car) — one feature, one sealed group ---
    /**
     * The service-log feature's screens, grouped under a single sealed parent.
     *
     * The keys still live in this shared registry (features never import each
     * other), but nesting them keeps the feature's slice of the graph cohesive and
     * lets a `when` over a [ServiceLog] key be exhaustive. Every screen is per-car,
     * so [carId] is hoisted onto the parent. The list's empty view is a UI state of
     * [List], not a separate destination.
     */
    sealed interface ServiceLog : OdoDestination {
        /** The car whose service record these screens belong to. */
        val carId: String

        /** The service-log list — the feature's home (Ledger 1a / Timeline 1b). */
        data class List(override val carId: String) : ServiceLog
        /** A single service entry's detail screen. */
        data class Detail(val logId: String, override val carId: String) : ServiceLog
        /** The add/edit form; [editLogId] non-null puts it in edit mode (same screen). */
        data class AddEdit(override val carId: String, val editLogId: String? = null) : ServiceLog
        /** Report an overcharge on a specific (flagged) entry — reached from its detail. */
        data class ReportOvercharge(val logId: String, override val carId: String) : ServiceLog
    }

    /**
     * Bill-scanner flow — its own feature; the log form + list empty state
     * deep-link into [BillScanner.Capture]. Capture routes to [BillScanner.Review],
     * where the AI-extracted fields are confirmed before saving. Features never
     * import billscanner — they navigate through this shared registry.
     */
    sealed interface BillScanner : OdoDestination {
        /** Camera viewfinder — capture a photo or pick one from the gallery. */
        data object Capture : BillScanner
        /** Review + confirm the AI-extracted bill details before saving. */
        data object Review : BillScanner
        /** The fairness verdict — how the reviewed bill compares to the city average. */
        data object Fairness : BillScanner
        /** Terminal success after the reviewed bill is saved to the log. */
        data object SaveSuccess : BillScanner
        /** Terminal success after an overcharge is anonymously reported. */
        data object ReportSuccess : BillScanner
        /** Error state — the AI couldn't read the bill (retry or enter manually). */
        data object ScanError : BillScanner
    }

    /** Cost tracker — the per-km "running cost" breakdown for the car. Its own feature. */
    data object CostTracker : OdoDestination

    /**
     * Document vault — the car's papers (insurance, PUC, RC, licence) and their renewal
     * status. Modelled as a group from the start: the [Vault] overview ships now, with a
     * per-document detail + an add/edit form to follow.
     */
    sealed interface Documents : OdoDestination {
        /** The vault overview — every tracked document + its status. */
        data object Vault : Documents
        /** Add a document — pick a type + how to capture it (scan / upload / import). */
        data object Add : Documents
        /** A single document's detail — policy info, expiry, coverage, file actions. */
        data object Detail : Documents
    }

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
