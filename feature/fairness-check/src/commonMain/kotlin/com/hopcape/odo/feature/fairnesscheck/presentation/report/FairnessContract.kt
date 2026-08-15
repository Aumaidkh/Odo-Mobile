package com.hopcape.odo.feature.fairnesscheck.presentation.report

/** What the owner did on the fairness report, as data. */
internal sealed interface FairnessEvent {

    /** "Report overcharge" — only offered when the check found one on a stored entry. */
    data object ReportTapped : FairnessEvent

    /** "Done", and the back arrow: both leave the errand the report ends. */
    data object DoneTapped : FairnessEvent

    /**
     * The report is on screen — on opening it, and again every time it is uncovered.
     *
     * What it says depends on the owner's city, and setting one is a trip to another screen
     * and back. Without this the report would still be showing "we don't know your city"
     * about a city that has since been set.
     */
    data object Shown : FairnessEvent

    /** "Set your city" on the no-city state. */
    data object SetCityTapped : FairnessEvent

    /** "Try again" after the check failed. */
    data object RetryTapped : FairnessEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface FairnessEffect {

    /** File an overcharge report against the entry this check was about. */
    data class OpenReportOvercharge(val logId: String, val carId: String) : FairnessEffect

    /**
     * Open the profile editor, which is where the city is set.
     *
     * The editor, not the profile root: the owner tapped a button that named one field, and
     * landing them on a settings list to find it themselves is not an answer to that.
     */
    data object OpenEditProfile : FairnessEffect

    /**
     * Leave the whole errand, not just this screen.
     *
     * A step back would land on the confirm step for a bill that is already saved. Every
     * step of the scan comes off instead, which puts the owner back where they started it.
     */
    data object LeaveFlow : FairnessEffect
}
