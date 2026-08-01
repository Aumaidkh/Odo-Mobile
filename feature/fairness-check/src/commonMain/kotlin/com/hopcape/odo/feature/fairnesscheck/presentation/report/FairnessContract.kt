package com.hopcape.odo.feature.fairnesscheck.presentation.report

/** What the owner did on the fairness report, as data. */
internal sealed interface FairnessEvent {

    /** "Report overcharge" — only offered when the check found one on a stored entry. */
    data object ReportTapped : FairnessEvent

    /** "Done", and the back arrow: both leave the report. */
    data object DoneTapped : FairnessEvent

    /** "Set your city" on the no-city state. */
    data object SetCityTapped : FairnessEvent

    /** "Try again" after the check failed. */
    data object RetryTapped : FairnessEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface FairnessEffect {

    /** File an overcharge report against the entry this check was about. */
    data class OpenReportOvercharge(val logId: String, val carId: String) : FairnessEffect

    /** Open the profile, where the city is set. */
    data object OpenProfile : FairnessEffect

    /** Leave the report. */
    data object NavigateBack : FairnessEffect
}
