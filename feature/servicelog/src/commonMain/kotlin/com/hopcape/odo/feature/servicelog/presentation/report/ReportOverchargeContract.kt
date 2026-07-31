package com.hopcape.odo.feature.servicelog.presentation.report

import com.hopcape.odo.core.domain.fairness.model.OverchargeReason

/** What the owner did on the report-overcharge screen. */
internal sealed interface ReportOverchargeEvent {
    data class ReasonSelected(val reason: OverchargeReason) : ReportOverchargeEvent
    data class NoteChanged(val note: String) : ReportOverchargeEvent
    data object SubmitClicked : ReportOverchargeEvent

    /** "Done" on the confirmation — the report is filed and the screen is finished. */
    data object DoneClicked : ReportOverchargeEvent
    data object BackClicked : ReportOverchargeEvent
}

/** One-shot handoffs, performed by the route host. */
internal sealed interface ReportOverchargeEffect {
    /** Leave the screen — from Done, from back, or from an entry that has gone. */
    data object NavigateBack : ReportOverchargeEffect
}
