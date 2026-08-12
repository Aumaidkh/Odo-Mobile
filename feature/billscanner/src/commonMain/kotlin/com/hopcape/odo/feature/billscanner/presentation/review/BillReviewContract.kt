package com.hopcape.odo.feature.billscanner.presentation.review

import com.hopcape.odo.core.navigation.FairnessLineInput
import kotlinx.datetime.LocalDate

/** What the owner did on the review screen, as data. */
internal sealed interface BillReviewEvent {

    data class WorkshopChanged(val value: String) : BillReviewEvent

    data class DateChanged(val value: LocalDate) : BillReviewEvent

    data class OdometerChanged(val value: String) : BillReviewEvent

    /** Save the entry, then hand the lines to the fairness check. */
    data object SaveTapped : BillReviewEvent

    data object RetakeTapped : BillReviewEvent

    data object BackTapped : BillReviewEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface BillReviewEffect {

    /**
     * The entry was written; the fairness check runs on what was saved.
     *
     * The lines travel as the shared registry's primitives, so the scanner never imports the
     * fairness feature — it only states what was paid, per job.
     */
    data class OpenFairness(
        val items: List<FairnessLineInput>,
        val logId: String,
        val carId: String,
    ) : BillReviewEffect

    /** The photo was no good; go back to the viewfinder. */
    data object Retake : BillReviewEffect

    /** Nothing could be read; offer the manual form instead. */
    data object OpenManualEntry : BillReviewEffect

    data object NavigateBack : BillReviewEffect
}
