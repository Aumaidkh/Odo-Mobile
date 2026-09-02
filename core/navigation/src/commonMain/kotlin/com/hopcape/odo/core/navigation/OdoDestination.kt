package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

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
@Serializable
sealed interface OdoDestination : NavKey {

    /** Destinations shown as roots in the bottom navigation bar. */
    @Serializable
    sealed interface TopLevel : OdoDestination {
        /** Short label rendered under the bottom-bar item. */
        val label: String
    }

    // --- Bottom-nav roots ---
    @Serializable
    data object Home : TopLevel { override val label = "Home" }

    /**
     * Profile / account — owned by `:feature:profile`. A sealed group: the [Root] account
     * home (a bottom-nav root), its full-screen editors, and the preference sheets the
     * rows open (the feature's entry provider tags each with [ModalBottomSheetSceneStrategy]
     * metadata). "Go Pro" and "Manage plan" reuse the shared [Paywall] key rather than
     * anything of their own.
     */
    @Serializable
    sealed interface Profile : OdoDestination {
        /** The profile / account home — reached from Home's avatar, not a bar tab. */
        @Serializable
        data object Root : Profile
        /** Edit-profile full screen. */
        @Serializable
        data object Edit : Profile
        /** Notification-settings full screen. */
        @Serializable
        data object Notifications : Profile
        /**
         * Privacy & permissions full screen — what Odo can reach, and what it keeps.
         *
         * Distinct from [Support.Privacy], which is the policy document. This one is
         * settings the owner changes; that one is text they read. The screen links to it.
         */
        @Serializable
        data object Privacy : Profile
        /**
         * "Delete my account & data" — the whole erase, confirm through to done.
         *
         * One destination rather than a confirmation plus a verification screen. The two
         * steps share the state that matters (the proof of the number, and how far the erase
         * got), and splitting them across Nav3 entries would mean each getting its own
         * ViewModel and neither owning the outcome.
         */
        @Serializable
        data object DeleteAccount : Profile
        /** Units-&-currency sheet. */
        @Serializable
        data object Units : Profile
        /** Appearance (theme + text size) sheet. */
        @Serializable
        data object Appearance : Profile
        /** Export-my-data sheet. */
        @Serializable
        data object Export : Profile
        /** Sign-out confirmation — shown as a sheet. */
        @Serializable
        data object SignOut : Profile

        /**
         * The QA config screen: every registered key, its resolved value, and which step
         * of the resolution order answered.
         *
         * Registered in every build, reachable in none but debug — the row that opens it
         * is behind `BuildInfo.isDebug`. Keeping the route registered rather than
         * conditionally absent matches how the refuel routes are handled: unreachable,
         * not removed.
         */
        @Serializable
        data object ConfigOverrides : Profile
    }

    /**
     * Garage — the car's "home base", owned by `:feature:garage`. A sealed group: the
     * [Home] bottom-nav root, the bottom-sheet destinations the car menu opens (the
     * feature's entry provider tags each with [ModalBottomSheetSceneStrategy] metadata),
     * and two full-screen editors. Like Timeline it is an aggregator — logging a service,
     * opening a document or scanning a bill reuse the ServiceLog / Documents / BillScanner
     * keys rather than anything of its own.
     */
    @Serializable
    sealed interface Garage : OdoDestination {
        /** The garage tab root — the car home-base overview. */
        @Serializable
        data object Home : Garage, TopLevel { override val label = "Garage" }
        /** Car actions sheet (⋮): edit · export · remove. */
        @Serializable
        data object CarActions : Garage
        /** Update-odometer sheet. */
        @Serializable
        data object UpdateOdometer : Garage
        /** "Add to service history" sheet: scan · manual · document. */
        @Serializable
        data object AddToHistory : Garage
        /** Export-car-record sheet. */
        @Serializable
        data object Export : Garage
        /** Remove-car confirmation — shown as a sheet. */
        @Serializable
        data object RemoveCar : Garage
        /** Edit-car full screen. */
        @Serializable
        data object EditCar : Garage
        /** Add-a-car full screen. */
        @Serializable
        data object AddCar : Garage
    }

    /**
     * Reminders flow — its own feature. Modelled as a group from the start (a Manage
     * screen, an add-reminder flow, and per-reminder detail follow); [List] is the
     * bottom-nav root, so this whole area lives under one shared key.
     */
    @Serializable
    sealed interface Reminders : OdoDestination {
        /** The reminders home — reached from Home's bell, not a bar tab. */
        @Serializable
        data object List : Reminders
        /** Notification + reminder preferences — reached from the home's "Manage". */
        @Serializable
        data object Settings : Reminders
        /**
         * Create a custom reminder — reached from the home's "+ Add" — or edit one when
         * [reminderId] names it (the actions sheet's "Reschedule").
         */
        @Serializable
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
        @Serializable
        data class Actions(
            val kind: String,
            val dueOn: String?,
            val customId: String?,
            val title: String,
            val due: String,
            val icon: String,
        ) : Reminders
    }

    /**
     * Auto-odometer — background trip tracking that keeps the odometer current without
     * manual entry, owned by `:feature:auto-odometer`. A sealed group: [Education] explains
     * the feature and its privacy stance, [NotificationRationale] makes the case for alerts,
     * [DevicePicker] pairs the car's stereo,
     * [PermissionSetup] stages the location/notification (+ activity-recognition) asks,
     * [TripLogged] surfaces the "N km added" moment after a drive, and [Settings] holds the
     * tracking toggle, trigger device and privacy controls.
     *
     * `:core:triptracker` (docs/TRIPTRACKER_PLAN.md) owns the tracking engine and its own
     * `TriggerMode` enum, but `:core:navigation` does not depend on that module just to type
     * a route argument — [AutoOdometerFlowMode] is a small nav-local redeclaration, and the
     * feature maps between the two at its ViewModel/screen boundary.
     */
    @Serializable
    sealed interface AutoOdometer : OdoDestination {

        /**
         * Which enrollment path a flow is on — the escape hatch for cars without a
         * Bluetooth stereo (docs/AUTO_ODOMETER_PLAN.md §1.1).
         */
        enum class AutoOdometerFlowMode { STEREO, NO_STEREO }

        /**
         * "Your reading stays current on its own" — the how-it-works + privacy explainer
         * (M2). [mode] picks the STEREO copy or the no-Bluetooth variant.
         */
        @Serializable
        data class Education(val mode: AutoOdometerFlowMode = AutoOdometerFlowMode.STEREO) : AutoOdometer

        /**
         * "You'll know when a trip is logged" — Odo's own case for `POST_NOTIFICATIONS`,
         * between the explainer and the first permission the feature actually needs.
         *
         * A step rather than a dialog raised on the way out of [Education]: that put the
         * system prompt on screen with the Bluetooth rationale already drawn behind it, so the
         * owner was answering a question nothing had asked them. Reached only when the system
         * will still prompt — see the education route.
         */
        @Serializable
        data class NotificationRationale(
            val mode: AutoOdometerFlowMode = AutoOdometerFlowMode.STEREO,
        ) : AutoOdometer

        /** "Which one is your car?" — pick the bonded stereo that triggers trips (M3). */
        @Serializable
        data object DevicePicker : AutoOdometer

        /**
         * "One last thing" — the staged permission checklist (location, notifications, and
         * activity-recognition on the [mode] == NO_STEREO branch only) (M4).
         */
        @Serializable
        data class PermissionSetup(val mode: AutoOdometerFlowMode = AutoOdometerFlowMode.STEREO) : AutoOdometer

        /**
         * The trip-logged moment: odometer drum, stats and the service-due nudge (M6).
         * Surfaced on next app open rather than a push notification (D4); [tripId] names
         * the trip, as a `TripId` value's raw string — `:core:navigation` holds no domain
         * types, so the feature maps it back at the boundary.
         */
        @Serializable
        data class TripLogged(val tripId: String) : AutoOdometer

        /** Tracking toggle, trigger device, monthly stats, privacy controls (M7). */
        @Serializable
        data object Settings : AutoOdometer
    }

    // --- Nested / argument-carrying destinations ---
    @Serializable
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
    @Serializable
    sealed interface ServiceLog : OdoDestination {
        /** The car whose service record these screens belong to. */
        val carId: String

        /** The service-log list — the feature's home (Ledger 1a / Timeline 1b). */
        @Serializable
        data class List(override val carId: String) : ServiceLog
        /** A single service entry's detail screen. */
        @Serializable
        data class Detail(val logId: String, override val carId: String) : ServiceLog
        /** The add/edit form; [editLogId] non-null puts it in edit mode (same screen). */
        @Serializable
        data class AddEdit(override val carId: String, val editLogId: String? = null) : ServiceLog
        /** Report an overcharge on a specific (flagged) entry — reached from its detail. */
        @Serializable
        data class ReportOvercharge(val logId: String, override val carId: String) : ServiceLog
        /**
         * Share as a PDF — shown as a bottom-sheet destination. With [logId] null the
         * document is the car's whole verified record; with it set, the document is that
         * one entry's bill, items and all.
         */
        @Serializable
        data class Share(override val carId: String, val logId: String? = null) : ServiceLog
    }

    /**
     * Bill-scanner flow — its own feature; the log form + list empty state
     * deep-link into [BillScanner.Capture]. Capture routes to [BillScanner.Review],
     * where the AI-extracted fields are confirmed before saving. Features never
     * import billscanner — they navigate through this shared registry.
     */
    @Serializable
    sealed interface BillScanner : OdoDestination {
        /**
         * Camera viewfinder — capture a photo or pick one from the gallery.
         *
         * [target] is what the caller came to scan. One destination rather than three,
         * because all three are the same camera, the same permission and the same capture;
         * only the frame guide and what happens to the photo afterwards differ.
         *
         * [documentType] is the paper the caller already knows it wants, as a
         * `DocumentType` name — the vault's RC row opens the scanner knowing it is an RC.
         * Null when nobody said, which is when the read's own guess is used instead. A
         * string because a key is serialized into the back stack, and because
         * `:core:navigation` holds no domain types.
         */
        @Serializable
        data class Capture(
            val target: ScanTarget = ScanTarget.Bill,
            val documentType: String? = null,
        ) : BillScanner

        /**
         * Review + confirm the AI-extracted bill details before saving.
         *
         * [photoKey] is where the captured bill was stored, as a
         * `PlatformFileStore` key. Null when the flow was reached without a photo.
         */
        @Serializable
        data class Review(val photoKey: String? = null) : BillScanner

        /**
         * Confirm a scanned paper's type and dates before it is filed in the vault.
         *
         * Its own key rather than a mode of [Review] because the two confirm different things
         * and end somewhere different — one in the service log, one in the document vault.
         *
         * [documentType] carries what the caller already knew, the same way [Capture] does.
         */
        /**
         * [origin] says how the file reached the app, because the row records that and the
         * two ways in end at the same screen. An enum of exactly two cases rather than the
         * source's name as a string: neither case earns the Verified badge, so no caller can
         * claim a document came from DigiLocker by naming it.
         */
        @Serializable
        data class DocumentReview(
            val photoKey: String,
            val documentType: String? = null,
            val origin: DocumentOrigin = DocumentOrigin.Scanned,
        ) : BillScanner

        /** Terminal success after the reviewed bill is saved to the log. */
        @Serializable
        data object SaveSuccess : BillScanner
        /** Terminal success after an overcharge is anonymously reported. */
        @Serializable
        data object ReportSuccess : BillScanner
        /** Error state — the AI couldn't read the bill (retry or enter manually). */
        @Serializable
        data object ScanError : BillScanner
    }

    /**
     * Logging a tank of fuel — owned by `:feature:refuel`.
     *
     * Every way of capturing a fill ends in this group. A payment notification, a photo of
     * a pump display and the owner opening the form themselves all build the same draft and
     * land on [Confirm]; only what filled the draft in differs.
     * That is why the draft travels as a [FuelFillDraftInput] on the key rather than each
     * capture route having a confirm screen of its own.
     */
    @Serializable
    sealed interface Refuel : OdoDestination {

        /**
         * The form the owner opens themselves, prefilled from their last visit.
         *
         * No arguments: what it prefills with is read when it opens, and a key carrying
         * yesterday's station would show a stale one after a back-stack restore.
         */
        @Serializable
        data object Log : Refuel

        /**
         * Confirm a captured fill before it is written.
         *
         * [draft] is what the capture channel managed to read. It is a navigation-layer
         * type rather than the domain's `FuelFillDraft` because a key is serialized into
         * the back stack and `:core:navigation` holds no domain types.
         */
        @Serializable
        data class Confirm(val draft: FuelFillDraftInput) : Refuel

        /** Terminal success — what was logged, and what the tank returned. */
        @Serializable
        data class Logged(val fillId: String) : Refuel

        /**
         * Fills that were detected but never answered.
         *
         * No arguments: what it lists is read when it opens. A key carrying the detections
         * would show a stale set after a back-stack restore, and this screen exists precisely
         * because those detections outlive the moment they happened.
         */
        @Serializable
        data object Pending : Refuel

        /**
         * The opt-in for reading payment notifications, and the settings behind it.
         *
         * One key rather than two: before the owner opts in the screen explains what it
         * would read, and afterwards the same screen is where they change or revoke it.
         */
        @Serializable
        data object AutoDetect : Refuel
    }

    /**
     * Cost tracker — the per-km "running cost" breakdown for the car. A sealed group: the
     * [Home] root (a bottom-nav root, labelled "Costs" in the bar) plus the sheet where the
     * owner corrects the fuel rate the estimate is built on.
     */
    @Serializable
    sealed interface CostTracker : OdoDestination {

        @Serializable
        data object Home : CostTracker, TopLevel { override val label = "Costs" }

        /**
         * "What do you pay for fuel?" — a bottom sheet. Odo's own prices are approximate
         * and refreshed at best weekly, so the owner can state their own and have every
         * figure rebuilt on it.
         */
        @Serializable
        data object FuelRate : CostTracker
    }

    /**
     * Timeline — the car's unified activity feed (services · documents · health-score
     * changes · milestones), owned by `:feature:timeline`. A sealed group: the [List]
     * root plus its "show in timeline" [Filter] sheet. An entry's detail reuses
     * [ServiceLog.Detail] and sharing reuses [ServiceLog.Share] — Timeline never
     * reimplements them.
     */
    @Serializable
    sealed interface Timeline : OdoDestination {
        /** The timeline tab root — the activity feed. */
        @Serializable
        data object List : Timeline, TopLevel { override val label = "Timeline" }
        /** "Show in timeline" filter sheet. */
        @Serializable
        data object Filter : Timeline
    }

    /**
     * Pro paywall — one screen, context-framed by [trigger] (why it was shown). Reached from
     * every "Unlock with Pro" affordance. Primitives only, so `:core:navigation` stays
     * domain-free: [amountPaise] frames the "you just saved" variant, [freeScans] the
     * "0 scans left" variant.
     */
    @Serializable
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
    @Serializable
    sealed interface HealthScore : OdoDestination {
        /** The score detail — dial, delta, and the factor breakdown. */
        @Serializable
        data object Detail : HealthScore
        /** "How your score works" — shown as a bottom-sheet destination from the (i) button. */
        @Serializable
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
    @Serializable
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
    @Serializable
    sealed interface Documents : OdoDestination {
        /** The vault overview — every tracked document + its status. */
        @Serializable
        data object Vault : Documents

        /**
         * Add a document — pick a type, then how to capture it (scan / upload / import).
         * [prefillType] names the type when the flow was opened from a vault row's "Add",
         * as the `DocumentType` enum name; `null` opens on the default.
         */
        @Serializable
        data class Add(val prefillType: String? = null) : Documents

        /** A single document's detail: expiry, reminder, and the file actions. */
        @Serializable
        data class Detail(val documentId: String) : Documents

        /** Terminal success after [documentId] was added to the vault. */
        @Serializable
        data class AddSuccess(val documentId: String) : Documents

        /** Share a document — shown as a bottom-sheet destination. */
        @Serializable
        data class Share(val documentId: String) : Documents

        /**
         * Correct a document's issue and expiry dates — a bottom-sheet destination.
         *
         * Its own key rather than a mode of [Detail], because it is where a document filed
         * with no expiry gets one, and that is what turns it into a reminder.
         */
        @Serializable
        data class EditDates(val documentId: String) : Documents
    }

    /**
     * Full-screen reader for a file the app stored, named by its [storageKey] — a scanned
     * bill, an insurance PDF, a photographed RC.
     *
     * Top level rather than part of [Documents], because a service log's bill and a scan
     * being reviewed are not vault documents and would have no business reaching a key in
     * that group. Keyed on the stored file rather than on the row that owns it, so a feature
     * opens the viewer without the viewer having to know that feature's ids.
     *
     * [title] is what the top bar shows; pass what the owner called the thing they tapped.
     * The entry is registered by `:shared`, which is the one module that already has the file
     * reader, the design system and the navigation host.
     *
     * [remoteBucket] names the storage bucket to fall back to when the file is not on this
     * device — the case on a second phone, which syncs the row that names a bill but not the
     * bytes behind it. It is the bucket's *name* rather than the type, so this module stays
     * free of data-layer imports. Leave it null for a file that is always local, such as a
     * scan the owner has just taken.
     */
    @Serializable
    data class FilePreview(
        val storageKey: String,
        val title: String? = null,
        val remoteBucket: String? = null,
    ) : OdoDestination {
        /**
         * The buckets a preview can fall back to, by name.
         *
         * Named here rather than taking the storage layer's own type, because a `:feature:*`
         * module does not depend on `:core:data` and this key must stay primitives-only.
         * `:shared` maps a name back to that type when it resolves the file, and a test there
         * — the one place that sees both — holds these to the enum they stand for.
         */
        companion object {
            const val BUCKET_DOCUMENTS = "DOCUMENTS"
            const val BUCKET_BILL_PHOTOS = "BILL_PHOTOS"
        }
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
    @Serializable
    sealed interface Support : OdoDestination {
        /** The Help & support hub — shown as a bottom-sheet destination. */
        @Serializable
        data object Help : Support
        /** Search the help articles — reached from the hub's search box. */
        @Serializable
        data object Search : Support
        /** "Something broken or wrong" — a bug report form. */
        @Serializable
        data object ReportProblem : Support
        /** "Request a feature" — an idea/suggestion form. */
        @Serializable
        data object SuggestIdea : Support
        /** "A benchmark looks off" — dispute a fairness price data point. */
        @Serializable
        data object FlagPriceData : Support
        /**
         * Rate Odo — a bottom sheet that asks for stars, then offers both the store listing
         * and a private message. A key rather than a direct hand-off, unlike Email and
         * Terms, because there is a screen here to show before anything leaves the app.
         */
        @Serializable
        data object Rate : Support
        /** Frequently asked questions. */
        @Serializable
        data object Faqs : Support
        /** Privacy policy. */
        @Serializable
        data object Privacy : Support
        /** Open-source licences. */
        @Serializable
        data object Licences : Support
    }

    // --- Onboarding flow (first-run car setup) ---
    /** Intro carousel shown on first launch, before car setup. */
    @Serializable
    data object Welcome : OdoDestination

    /**
     * The video intro — the variant [Welcome] is replaced by when
     * `onboarding_video_enabled` is on. Two pages, Smart Refuel then Bill Scanner, and then
     * the same car setup [Welcome] leads to.
     */
    @Serializable
    data object WelcomeVideo : OdoDestination
    @Serializable
    data object Onboarding : OdoDestination

    /**
     * Asks the owner a set of questions (#394) and stores the answers.
     *
     * [keys] names which questions to ask, so onboarding can run the whole set while the
     * profile screen edits one. Plain strings, not a feature type, so `:core:navigation`
     * stays free of the questionnaire's presentation types — the registry resolves them.
     *
     * An unknown key is skipped rather than failing: a deep link built by an older build
     * should ask what it can, not refuse to open.
     */
    @Serializable
    data class Questionnaire(val keys: List<String>) : OdoDestination

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
    @Serializable
    sealed interface Auth : OdoDestination {
        /**
         * Where to land once the number is verified — or once the owner skips. Carried
         * through every step so auth never needs to know *why* it was entered: onboarding
         * hands over the goal-based surface it would otherwise have gone to itself.
         */
        val next: OdoDestination

        /** Enter the mobile number the 6-digit code is sent to. */
        @Serializable
        data class Phone(override val next: OdoDestination = Home) : Auth

        /**
         * Enter (or auto-read) the 6-digit code.
         *
         * @param phone the normalized number the code went to (digits only, no dialling
         *   code) — carried so the "Sent to …" line states the real number rather than a
         *   placeholder.
         */
        @Serializable
        data class Otp(val phone: String, override val next: OdoDestination = Home) : Auth

        /** Terminal progress while the code is checked, then hands off to [next]. */
        @Serializable
        data class Verifying(override val next: OdoDestination = Home) : Auth
    }


    @Serializable
    sealed interface Challan: OdoDestination {
        @Serializable
        data object List: Challan
        // List
        @Serializable
        data object Lookup: Challan
        // Lookup
        @Serializable
        data class Result(val regNo: String): Challan
        // Result
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
 * How the file being confirmed reached the app.
 *
 * Both cases are the owner's own copy, so neither is treated as verified. DigiLocker is
 * deliberately absent: an official copy is never confirmed on this screen, and leaving it out
 * means a navigation key cannot be used to claim one.
 */
enum class DocumentOrigin {

    /** Photographed in the app. */
    Scanned,

    /** A PDF or image picked from the device. */
    Uploaded,
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

    /**
     * The pump's own display: the amount, the volume and the rate it is showing.
     *
     * The one capture channel that works in every market and needs no payment app at all —
     * a pump shows those three numbers whether the owner paid by card, by phone or in cash.
     */
    PumpDisplay,
}

/**
 * A captured fill on its way to the confirm step, in navigation-layer primitives.
 *
 * A mirror of the domain's `FuelFillDraft` rather than the type itself, because a key is
 * serialized into the back stack and `:core:navigation` may not depend on `:core:domain`.
 * The refuel feature maps it both ways at the boundary.
 *
 * Every field is optional for the same reason it is on the domain type: each capture channel
 * knows a different subset, and what is missing is what the confirm step asks for. The
 * `*Origin` strings name a `FieldOrigin`; an unrecognised one reads as unknown, so a key
 * written by an older build cannot claim a number was observed.
 */
@Serializable
data class FuelFillDraftInput(
    val source: String,
    val amountPaise: Long? = null,
    val amountOrigin: String? = null,
    val quantityMilli: Long? = null,
    val quantityOrigin: String? = null,
    val pricePerUnitPaise: Long? = null,
    val priceOrigin: String? = null,
    val odometerKm: Int? = null,
    val odometerOrigin: String? = null,
    val stationName: String? = null,
    val transactionRef: String? = null,
)

/**
 * One line for a [OdoDestination.Fairness] check — primitives only, so `:core:navigation`
 * stays domain-free (the fairness feature maps [category]/[amountPaise] to domain types).
 */
@Serializable
data class FairnessLineInput(
    val label: String,
    val category: String?,
    val amountPaise: Long,
)