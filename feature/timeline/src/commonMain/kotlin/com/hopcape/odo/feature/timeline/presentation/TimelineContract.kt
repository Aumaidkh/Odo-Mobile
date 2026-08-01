package com.hopcape.odo.feature.timeline.presentation

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId

/** What the owner did on the timeline, as data. */
internal sealed interface TimelineEvent {

    /** The filter button in the header. */
    data object FilterTapped : TimelineEvent

    /** The share button in the header. */
    data object ShareTapped : TimelineEvent

    /** A service card — opens that entry's detail. */
    data class ServiceTapped(val id: ServiceLogId) : TimelineEvent

    /** "Add bill" on a self-reported entry. */
    data class AddBillTapped(val id: ServiceLogId) : TimelineEvent

    /** "Scan first bill" on the empty feed. */
    data object ScanFirstTapped : TimelineEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface TimelineEffect {

    /** Open the "show in timeline" sheet. */
    data object OpenFilter : TimelineEffect

    /** Share the car's record; carries the car because the share sheet is per car. */
    data class ShareRecord(val carId: String) : TimelineEffect

    /** Open a logged service's detail. */
    data class OpenService(val logId: String, val carId: String) : TimelineEffect

    /** Open the bill scanner — from the empty state, or to back up an entry. */
    data object OpenScanner : TimelineEffect
}
