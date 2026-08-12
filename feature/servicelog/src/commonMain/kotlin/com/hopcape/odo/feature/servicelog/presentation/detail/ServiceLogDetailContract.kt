package com.hopcape.odo.feature.servicelog.presentation.detail

import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId

/** What the owner did on one entry's detail. */
internal sealed interface ServiceLogDetailEvent {

    /** "Share verified record" — offered only for a verified entry. */
    data object ShareClicked : ServiceLogDetailEvent

    /** "Report this overcharge" — offered only when the entry is over the city average. */
    data object ReportOverchargeClicked : ServiceLogDetailEvent

    data object EditClicked : ServiceLogDetailEvent

    /** "Add a bill to verify" — offered while the entry is still self-reported. */
    data object AttachBillClicked : ServiceLogDetailEvent

    /** The picker came back with a file. [pickedRef] is a borrowed handle, not a stored one. */
    data class BillPicked(val pickedRef: String) : ServiceLogDetailEvent

    /** The attached bill was tapped — open it full screen. */
    data object BillTapped : ServiceLogDetailEvent

    /** "Check fairness" — benchmark this entry's lines against the city. */
    data object CheckFairnessClicked : ServiceLogDetailEvent

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

    /** Open the platform picker; the file comes back as [ServiceLogDetailEvent.BillPicked]. */
    data object PickBillPhoto : ServiceLogDetailEffect

    /**
     * Show the attached bill full screen. This is what makes the entry's "Verified" badge
     * mean something: whoever it is shown to can check the paper behind it.
     */
    data class PreviewBill(val storageKey: String) : ServiceLogDetailEffect

    /**
     * Open the shared fairness report for this entry's lines. The lines travel because the
     * fairness feature benchmarks what it is given — it never reads the service log, which
     * is what keeps the two features from importing each other.
     */
    data class OpenFairness(val id: ServiceLogId, val lines: List<FairnessCheckLine>) : ServiceLogDetailEffect

    /**
     * The entry is gone — leave the screen. Separate from [NavigateBack] because it is a
     * consequence, not a request: the list behind this screen is already re-reading itself.
     */
    data object Deleted : ServiceLogDetailEffect

    data object NavigateBack : ServiceLogDetailEffect
}

/**
 * One line handed to the fairness check — primitives only, because the navigation key that
 * carries it is primitive too. The route maps this onto that key.
 */
internal data class FairnessCheckLine(
    val label: String?,
    val categoryName: String?,
    val amountPaise: Long,
)
