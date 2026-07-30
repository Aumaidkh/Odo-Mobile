package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId

/** What the owner did on one entry's detail. */
internal sealed interface ServiceLogDetailEvent {

    /** "Share verified record" — offered only for a verified entry. */
    data object ShareClicked : ServiceLogDetailEvent

    /** "Report this overcharge" — offered only when the entry is over the city average. */
    data object ReportOverchargeClicked : ServiceLogDetailEvent

    data object EditClicked : ServiceLogDetailEvent

    /**
     * Deleting is three taps, not one: asking, confirming, and backing out. Each is its own
     * event because each moves [DeleteUiState] somewhere different.
     */
    sealed interface Delete : ServiceLogDetailEvent {
        data object Requested : Delete
        data object Confirmed : Delete
        data object Dismissed : Delete
    }

    data object BackClicked : ServiceLogDetailEvent
}

/** One-shot handoffs, performed by the route host. */
internal sealed interface ServiceLogDetailEffect {
    data object OpenShareRecord : ServiceLogDetailEffect
    data class OpenReportOvercharge(val id: ServiceLogId) : ServiceLogDetailEffect
    data class OpenEditForm(val id: ServiceLogId) : ServiceLogDetailEffect

    /**
     * The entry is gone — leave the screen. Separate from [NavigateBack] because it is a
     * consequence, not a request: the list behind this screen is already re-reading itself.
     */
    data object Deleted : ServiceLogDetailEffect

    data object NavigateBack : ServiceLogDetailEffect
}
