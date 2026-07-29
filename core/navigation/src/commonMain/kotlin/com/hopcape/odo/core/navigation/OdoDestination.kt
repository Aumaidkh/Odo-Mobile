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

    /**
     * Profile / account — owned by `:feature:profile`. A sealed group: the [Root] account
     * home (a bottom-nav root), its full-screen editors, and the preference sheets the
     * rows open (the feature's entry provider tags each with [ModalBottomSheetSceneStrategy]
     * metadata). "Go Pro" and "Manage plan" reuse the shared [Paywall] key rather than
     * anything of their own.
     */
    sealed interface Profile : OdoDestination {
        /** The profile / account home — reached from Home's avatar, not a bar tab. */
        data object Root : Profile
        /** Edit-profile full screen. */
        data object Edit : Profile
        /** Notification-settings full screen. */
        data object Notifications : Profile
        /** Units-&-currency sheet. */
        data object Units : Profile
        /** Appearance (theme + text size) sheet. */
        data object Appearance : Profile
        /** Export-my-data sheet. */
        data object Export : Profile
        /** Sign-out confirmation — shown as a sheet. */
        data object SignOut : Profile
    }

    /**
     * Garage — the car's "home base", owned by `:feature:garage`. A sealed group: the
     * [Home] bottom-nav root, the bottom-sheet destinations the car menu opens (the
     * feature's entry provider tags each with [ModalBottomSheetSceneStrategy] metadata),
     * and two full-screen editors. Like Timeline it is an aggregator — logging a service,
     * opening a document or scanning a bill reuse the ServiceLog / Documents / BillScanner
     * keys rather than anything of its own.
     */
    sealed interface Garage : OdoDestination {
        /** The garage tab root — the car home-base overview. */
        data object Home : Garage, TopLevel { override val label = "Garage" }
        /** Car actions sheet (⋮): edit · switch · add · export · remove. */
        data object CarActions : Garage
        /** Update-odometer sheet. */
        data object UpdateOdometer : Garage
        /** "Add to service history" sheet: scan · manual · document. */
        data object AddToHistory : Garage
        /** Switch-car sheet. */
        data object SwitchCar : Garage
        /** Export-car-record sheet. */
        data object Export : Garage
        /** Remove-car confirmation — shown as a sheet. */
        data object RemoveCar : Garage
        /** Edit-car full screen. */
        data object EditCar : Garage
        /** Add-a-car full screen. */
        data object AddCar : Garage
    }

    /**
     * Reminders flow — its own feature. Modelled as a group from the start (a Manage
     * screen, an add-reminder flow, and per-reminder detail follow); [List] is the
     * bottom-nav root, so this whole area lives under one shared key.
     */
    sealed interface Reminders : OdoDestination {
        /** The reminders home — reached from Home's bell, not a bar tab. */
        data object List : Reminders
        /** Notification + reminder preferences — reached from the home's "Manage". */
        data object Settings : Reminders
        /** Create a custom reminder — reached from the home's "+ Add". */
        data object New : Reminders
        /**
         * Actions for a "this week" reminder (reschedule / snooze / turn off) — shown as a
         * bottom-sheet destination from tapping the reminder's card. Primitives only, so
         * `:core:navigation` stays free of the feature's presentation types.
         */
        data class Actions(val title: String, val due: String, val icon: String) : Reminders
    }

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
        /** Share the car's verified record — shown as a bottom-sheet destination. */
        data class Share(override val carId: String) : ServiceLog
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
        /** Terminal success after the reviewed bill is saved to the log. */
        data object SaveSuccess : BillScanner
        /** Terminal success after an overcharge is anonymously reported. */
        data object ReportSuccess : BillScanner
        /** Error state — the AI couldn't read the bill (retry or enter manually). */
        data object ScanError : BillScanner
    }

    /**
     * Cost tracker — the per-km "running cost" breakdown for the car. Its own feature,
     * and a bottom-nav root (labelled "Costs" in the bar).
     */
    data object CostTracker : TopLevel { override val label = "Costs" }

    /**
     * Timeline — the car's unified activity feed (services · documents · health-score
     * changes · milestones), owned by `:feature:timeline`. A sealed group: the [List]
     * root plus its "show in timeline" [Filter] sheet. An entry's detail reuses
     * [ServiceLog.Detail] and sharing reuses [ServiceLog.Share] — Timeline never
     * reimplements them.
     */
    sealed interface Timeline : OdoDestination {
        /** The timeline tab root — the activity feed. */
        data object List : Timeline, TopLevel { override val label = "Timeline" }
        /** "Show in timeline" filter sheet. */
        data object Filter : Timeline
    }

    /**
     * Pro paywall — one screen, context-framed by [trigger] (why it was shown). Reached from
     * every "Unlock with Pro" affordance. Primitives only, so `:core:navigation` stays
     * domain-free: [amountPaise] frames the "you just saved" variant, [freeScans] the
     * "0 scans left" variant.
     */
    data class Paywall(
        val trigger: String = "GENERIC",
        val amountPaise: Long = 0L,
        val freeScans: Int = 0,
    ) : OdoDestination

    /**
     * Health Score — the 0–100 rule-based score + its factor breakdown. Its own feature.
     * A sealed group: the [Detail] screen plus the [Info] explainer, which is presented as a
     * bottom sheet (its entry is tagged with [ModalBottomSheetSceneStrategy] metadata).
     */
    sealed interface HealthScore : OdoDestination {
        /** The score detail — dial, delta, and the factor breakdown. */
        data object Detail : HealthScore
        /** "How your score works" — shown as a bottom-sheet destination from the (i) button. */
        data object Info : HealthScore
    }

    /**
     * Fairness check — a **reusable benchmarking utility**. A caller passes the minimal
     * input (what was paid, per job, and the city); the fairness feature runs the
     * analysis and shows the report. Any feature invokes it through this one shared key
     * (bill scanner, a logged entry, a standalone price check), so the benchmarking flow
     * lives in exactly one place.
     */
    data class Fairness(
        val city: String,
        val items: List<FairnessLineInput>,
    ) : OdoDestination

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
        /** Terminal success after a document is added to the vault. */
        data object AddSuccess : Documents
        /** Share a document (optionally redacted) — shown as a bottom-sheet destination. */
        data object Share : Documents
    }

    // --- Onboarding flow (first-run car setup) ---
    /** Intro carousel shown on first launch, before car setup. */
    data object Welcome : OdoDestination
    data object Onboarding : OdoDestination

    /**
     * Sign-in flow — phone → otp → verifying.
     *
     * Deliberately **after** car setup, never before it: Odo works fully offline, so first
     * run must not stall behind an OTP. Onboarding routes here on completion only when
     * there is no session yet (`SessionStatusProvider`), and the owner can skip — signing
     * in is a prompt, not a gate.
     *
     * Grouped so the whole flow pops in one command (`popUpTo = Auth.Phone(next),
     * inclusive = true`), which is why back can never land on sign-in afterwards.
     */
    sealed interface Auth : OdoDestination {
        /**
         * Where to land once the number is verified — or once the owner skips. Carried
         * through every step so auth never needs to know *why* it was entered: onboarding
         * hands over the goal-based surface it would otherwise have gone to itself.
         */
        val next: OdoDestination

        /** Enter the mobile number the 6-digit code is sent to. */
        data class Phone(override val next: OdoDestination = Home) : Auth

        /**
         * Enter (or auto-read) the 6-digit code.
         *
         * @param phone the normalized number the code went to (digits only, no dialling
         *   code) — carried so the "Sent to …" line states the real number rather than a
         *   placeholder.
         */
        data class Otp(val phone: String, override val next: OdoDestination = Home) : Auth

        /** Terminal progress while the code is checked, then hands off to [next]. */
        data class Verifying(override val next: OdoDestination = Home) : Auth
    }

    companion object {
        /**
         * Ordered bottom-navigation roots — the four tabs the dashboard shell renders,
         * split symmetrically around the central Scan action: Home · Timeline · [Scan] ·
         * Costs · Garage. Scan is a raised FAB, not a selectable root, so it isn't here.
         *
         * Reminders and Profile are deliberately absent: both are reached from Home's
         * header (the bell and the avatar), which keeps the bar to the four surfaces an
         * owner moves between rather than every screen that exists.
         */
        val topLevel: List<TopLevel> = listOf(Home, Timeline.List, CostTracker, Garage.Home)
    }
}

/**
 * One line for a [OdoDestination.Fairness] check — primitives only, so `:core:navigation`
 * stays domain-free (the fairness feature maps [category]/[amountPaise] to domain types).
 */
data class FairnessLineInput(
    val label: String,
    val category: String?,
    val amountPaise: Long,
)
