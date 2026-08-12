package com.hopcape.odo.feature.servicelog.presentation.list

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceRecordSummary
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.servicelog.presentation.list.model.ServiceLogDirection
import com.hopcape.odo.feature.servicelog.presentation.state.WorkDone
import kotlinx.datetime.LocalDate

/**
 * The filter chips over the list (mockup 1a: All / Verified / Flagged). Orthogonal to
 * the load phase, so it survives content re-emits and is meaningful before the first
 * load. 1b's timeline ignores it (shows everything) — the same state serves both.
 */
internal enum class ServiceLogFilter { ALL, VERIFIED, FLAGGED }

/**
 * How many entries each chip stands for — counted over *all* the car's entries, not the
 * filtered rows, so switching chips never changes the counts beside them.
 */
internal data class ServiceLogFilterCounts(
    val all: Int,
    val verified: Int,
    val flagged: Int,
) {
    operator fun get(filter: ServiceLogFilter): Int = when (filter) {
        ServiceLogFilter.ALL -> all
        ServiceLogFilter.VERIFIED -> verified
        ServiceLogFilter.FLAGGED -> flagged
    }

    companion object {
        val NONE = ServiceLogFilterCounts(all = 0, verified = 0, flagged = 0)
    }
}

/**
 * The fairness badge a single card shows — a **display** verdict with exactly the states
 * the mockups render, so the composable never re-derives it from raw domain. Mapped (by
 * [toCardUiState]) from [VerificationStatus] + the entry's stored `FairnessVerdict`:
 *  - [FairPrice]        — verified & at or under the city band ("Fair price")
 *  - [Overcharged]      — verified & over the city average ("Rs. 1,100 over")
 *  - [AddBillToVerify]  — self-reported, so no fairness check is possible yet
 *  - [NotEnoughData]    — judged, but on too thin a sample to state a verdict
 *  - [NotYetChecked]    — verified, but no city benchmark for the category yet
 */
internal sealed interface ServiceLogFairnessBadge {
    data object FairPrice : ServiceLogFairnessBadge
    data class Overcharged(val by: Amount) : ServiceLogFairnessBadge
    data object AddBillToVerify : ServiceLogFairnessBadge

    /**
     * The PRD's no-false-precision guardrail as a state: fewer than five data points is
     * not enough to call a price fair or unfair, and saying either would be a claim the
     * pool can't support. [estimate] carries the sample the UI must show alongside it.
     */
    data class NotEnoughData(val estimate: FairnessEstimate) : ServiceLogFairnessBadge

    data object NotYetChecked : ServiceLogFairnessBadge
}

/**
 * One row of the list — a display view of a service entry with exactly what a card
 * (ledger 1a) / timeline node (1b) needs, holding typed value objects ([Amount],
 * [Distance], [LocalDate], [WorkDone]) rather than the raw `ServiceLogEntry`. The UI
 * formats these; it never touches the domain aggregate.
 */
internal data class ServiceLogCardUiState(
    val id: ServiceLogId,
    val workshopName: String?,
    val serviceDate: LocalDate,
    val odometer: Distance,
    val amount: Amount,
    val verification: VerificationStatus,
    val workDone: WorkDone,
    val fairness: ServiceLogFairnessBadge,
)

/**
 * Service-log list render state. [content] is the mutually-exclusive load phase — a
 * sealed type, so "loading with a summary" or "empty with visible rows" can't be
 * represented and the UI's `when` is exhaustive.
 *
 * [filter] and [direction] stay top-level: both are choices the owner made about how to
 * look at the list, so they outlive any one emission of [content] (a re-read of the car's
 * entries must not silently drop them back to All / Ledger).
 */
internal data class ServiceLogListUiState(
    val content: Content = Content.Loading,
    val filter: ServiceLogFilter = ServiceLogFilter.ALL,
    val direction: ServiceLogDirection = ServiceLogDirection.LEDGER,
) {
    sealed interface Content {
        data object Loading : Content

        /** The car has no entries at all — the "log your first service" pitch. */
        data object Empty : Content

        /**
         * The car's entries could not be read. Distinct from [Empty] on purpose: an
         * unreadable log is not an empty one, and telling an owner with six services
         * that they have none is the worse of the two lies.
         */
        data class Failed(val message: UiText) : Content

        /**
         * A populated list. [cards] is the already-filtered/sorted rows the list renders;
         * the header stats span *all* entries:
         *  - [summary] powers both headers — 1a's "Total spent / N services" and 1b's
         *    record ring ("N of M verified", score, strength, resale uplift).
         *  - [savings] powers 1a's "Saved so far · N overcharges caught".
         *  - [counts] are the filter chips' counts.
         */
        data class Loaded(
            val cards: List<ServiceLogCardUiState>,
            val summary: ServiceRecordSummary,
            val savings: FairnessSavings,
            val counts: ServiceLogFilterCounts,
        ) : Content {
            /** True when the active filter matched nothing — rows are empty, the car isn't. */
            val isFilteredEmpty: Boolean get() = cards.isEmpty()
        }
    }
}
