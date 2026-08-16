package com.hopcape.odo.feature.dashboard.presentation.home

/** What the owner did on Home, as data. */
internal sealed interface HomeEvent {

    /** "See breakdown" on the health card. */
    data object BreakdownTapped : HomeEvent

    /** The attention card — where it goes depends on what it is about. */
    data object AttentionTapped : HomeEvent

    /** "Timeline" beside the recent-activity heading. */
    data object TimelineTapped : HomeEvent

    /** The recent-activity row; only a logged service opens anything. */
    data object RecentTapped : HomeEvent

    /** The bell in the header. */
    data object BellTapped : HomeEvent

    /** The avatar in the header. */
    data object ProfileTapped : HomeEvent

    /** "Scan your first bill", and the checklist's bill row. */
    data object ScanBillTapped : HomeEvent

    /**
     * "Log a fill" — the quick action that makes refuelling a two-tap job.
     *
     * On the home screen rather than inside the garage because the moment it serves is the
     * one just after paying at a pump, when the owner opens the app and is looking at this.
     */
    data object LogFillTapped : HomeEvent

    /** "Log fills automatically" — opens the explanation, not the permission. */
    data object AutoDetectTapped : HomeEvent

    /** The auto-odometer offer card — opens the education screen, not the permission. */
    data object AutoOdometerTapped : HomeEvent

    /** The checklist's documents row. */
    data object AddDocumentsTapped : HomeEvent

    /** "Add your car" on the no-car state. */
    data object AddCarTapped : HomeEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface HomeEffect {

    /** Open the health-score breakdown. */
    data object OpenHealthScore : HomeEffect

    /** Open the document vault — what a paper that needs renewing leads to. */
    data object OpenVault : HomeEffect

    /** Open the service log, which is where an overdue service gets dealt with. */
    data class OpenServiceLog(val carId: String) : HomeEffect

    /** Open the timeline tab. */
    data object OpenTimeline : HomeEffect

    /** Open a logged service's detail. */
    data class OpenService(val logId: String, val carId: String) : HomeEffect

    /** Open the reminders list. */
    data object OpenReminders : HomeEffect

    /** Open the owner's profile. */
    data object OpenProfile : HomeEffect

    /** Open the bill scanner — the North Star funnel's entry point from Home. */
    data object OpenScanner : HomeEffect

    /** Open the prefilled fuel-log form. */
    data object OpenLogFill : HomeEffect

    /** Open the auto-detect opt-in, which explains what would be read before asking. */
    data object OpenAutoDetect : HomeEffect

    /** Open the auto-odometer education screen — enrollment's front door. */
    data object OpenAutoOdometer : HomeEffect

    /** Automatic logging was tapped by an owner who does not have it. */
    data object OpenPaywall : HomeEffect

    /** Open the vault's add-document flow. */
    data object OpenAddDocument : HomeEffect

    /** Open onboarding's add-car flow. */
    data object OpenAddCar : HomeEffect
}
