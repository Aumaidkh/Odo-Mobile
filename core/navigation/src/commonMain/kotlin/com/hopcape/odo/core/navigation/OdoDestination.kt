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
        /** Car actions sheet (⋮): edit · export · remove. */
        data object CarActions : Garage
        /** Update-odometer sheet. */
        data object UpdateOdometer : Garage
        /** "Add to service history" sheet: scan · manual · document. */
        data object AddToHistory : Garage
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
        /**
         * Create a custom reminder — reached from the home's "+ Add" — or edit one when
         * [reminderId] names it (the actions sheet's "Reschedule").
         */
        data class New(val reminderId: String? = null) : Reminders
        /**
         * Actions for a "this week" reminder (reschedule / snooze / turn off) — shown as a
         * bottom-sheet destination from tapping the reminder's card. Primitives only, so
         * `:core:navigation` stays free of the feature's presentation types.
         *
         * [kind], [dueOn] (ISO date of the occurrence, absent for a distance target) and
         * [customId] identify the reminder so the sheet can act on it; [title], [due] and
         * [icon] are the display echo of the tapped card.
         */
        data class Actions(
            val kind: String,
            val dueOn: String?,
            val customId: String?,
            val title: String,
            val due: String,
            val icon: String,
        ) : Reminders
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
        /**
         * Camera viewfinder — capture a photo or pick one from the gallery.
         *
         * [target] is what the caller came to scan. One destination rather than three,
         * because all three are the same camera, the same permission and the same capture;
         * only the frame guide and what happens to the photo afterwards differ.
         */
        data class Capture(val target: ScanTarget = ScanTarget.Bill) : BillScanner

        /**
         * Review + confirm the AI-extracted bill details before saving.
         *
         * [photoKey] is where the captured bill was stored, as a
         * `PlatformFileStore` key. Null when the flow was reached without a photo.
         */
        data class Review(val photoKey: String? = null) : BillScanner

        /**
         * Confirm a scanned paper's type and dates before it is filed in the vault.
         *
         * Its own key rather than a mode of [Review] because the two confirm different things
         * and end somewhere different — one in the service log, one in the document vault.
         */
        data class DocumentReview(val photoKey: String) : BillScanner

        /**
         * Pay a scanned QR, then record the fill it bought.
         *
         * [payload] is the raw string read out of the code. Parsing it is the feature's job,
         * so `:core:navigation` stays free of any knowledge about UPI.
         */
        data class PayAtPump(val payload: String) : BillScanner
        /** Terminal success after the reviewed bill is saved to the log. */
        data object SaveSuccess : BillScanner
        /** Terminal success after an overcharge is anonymously reported. */
        data object ReportSuccess : BillScanner
        /** Error state — the AI couldn't read the bill (retry or enter manually). */
        data object ScanError : BillScanner
    }

    /**
     * Cost tracker — the per-km "running cost" breakdown for the car. A sealed group: the
     * [Home] root (a bottom-nav root, labelled "Costs" in the bar) plus the sheet where the
     * owner corrects the fuel rate the estimate is built on.
     */
    sealed interface CostTracker : OdoDestination {

        data object Home : CostTracker, TopLevel { override val label = "Costs" }

        /**
         * "What do you pay for fuel?" — a bottom sheet. Odo's own prices are approximate
         * and refreshed at best weekly, so the owner can state their own and have every
         * figure rebuilt on it.
         */
        data object FuelRate : CostTracker
    }

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
     * input (what was paid, per job); the fairness feature runs the analysis and shows the
     * report. Any feature invokes it through this one shared key (bill scanner, a logged
     * entry, a standalone price check), so the benchmarking flow lives in exactly one place.
     *
     * The city is deliberately **not** here: it is the owner's, read from their profile, and
     * a caller passing one would be a second answer to a question the app already has.
     *
     * [logId] and [carId] name the entry the check is about, when there is one. They are what
     * let the report offer "Report overcharge" — a standalone price check has nothing to
     * report against, so it passes neither and the action does not appear.
     */
    data class Fairness(
        val items: List<FairnessLineInput>,
        val logId: String? = null,
        val carId: String? = null,
    ) : OdoDestination

    /**
     * Document vault — the car's papers (insurance, PUC, RC, licence) and their renewal
     * status. Modelled as a group from the start: the [Vault] overview ships now, with a
     * per-document detail + an add/edit form to follow.
     */
    sealed interface Documents : OdoDestination {
        /** The vault overview — every tracked document + its status. */
        data object Vault : Documents

        /**
         * Add a document — pick a type, then how to capture it (scan / upload / import).
         * [prefillType] names the type when the flow was opened from a vault row's "Add",
         * as the `DocumentType` enum name; `null` opens on the default.
         */
        data class Add(val prefillType: String? = null) : Documents

        /** A single document's detail: expiry, reminder, and the file actions. */
        data class Detail(val documentId: String) : Documents

        /** Terminal success after [documentId] was added to the vault. */
        data class AddSuccess(val documentId: String) : Documents

        /** Share a document — shown as a bottom-sheet destination. */
        data class Share(val documentId: String) : Documents
    }

    /**
     * Help & support — owned by `:feature:support`. [Help] is the hub, presented as a
     * bottom-sheet destination from Profile's "Help & support" row; every other key here is
     * one of that sheet's rows.
     *
     * Grouped rather than scattered because support is one vertical capability: getting in
     * touch ([Chat] / [Email] / [Tickets]), sending feedback ([ReportProblem] /
     * [SuggestIdea] / [FlagPriceData] / [Rate]) and reading the legal + FAQ pages
     * ([Faqs] / [Terms] / [Privacy] / [Licences]). Profile reaches the hub through this
     * shared registry, so it never imports the support feature.
     *
     * [Email] and [Rate] are destinations only until the platform layer lands: both end in
     * a system hand-off (a mail composer, the Play Store listing) rather than a screen of
     * ours, so they will become `:core:platform` calls and lose their keys.
     */
    sealed interface Support : OdoDestination {
        /** The Help & support hub — shown as a bottom-sheet destination. */
        data object Help : Support
        /** Search the help articles — reached from the hub's search box. */
        data object Search : Support
        /** Live chat with a support agent. */
        data object Chat : Support
        /** Email support — a mail hand-off once the platform layer exists. */
        data object Email : Support
        /** The owner's raised tickets and their status. */
        data object Tickets : Support
        /** "Something broken or wrong" — a bug report form. */
        data object ReportProblem : Support
        /** "Request a feature" — an idea/suggestion form. */
        data object SuggestIdea : Support
        /** "A benchmark looks off" — dispute a fairness price data point. */
        data object FlagPriceData : Support
        /** Rate Odo — a Play Store hand-off once the platform layer exists. */
        data object Rate : Support
        /** Frequently asked questions. */
        data object Faqs : Support
        /** Terms of service. */
        data object Terms : Support
        /** Privacy policy. */
        data object Privacy : Support
        /** Open-source licences. */
        data object Licences : Support
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
        val topLevel: List<TopLevel> = listOf(Home, Timeline.List, CostTracker.Home, Garage.Home)
    }
}

/**
 * What the scanner is being pointed at, stated by whoever opened it.
 *
 * The owner can still switch once the camera is open — this is the starting point, not a
 * lock. Opening the scanner from the document vault should not begin on a bill frame.
 */
enum class ScanTarget {

    /** A workshop service bill: extract the date, odometer, line items and total. */
    Bill,

    /** A paper with an expiry date on it: insurance, PUC, RC or a licence. */
    Document,

    /** A payment QR at a fuel pump or a workshop counter. */
    PaymentQr,
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
