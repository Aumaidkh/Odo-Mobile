package com.hopcape.odo.feature.servicelog.presentation.list

import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceRecordSummary
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate

/**
 * The filter chips over the list (mockup 1a: All / Verified / Flagged). Orthogonal to
 * the load phase, so it survives content re-emits and is meaningful before the first
 * load. 1b's timeline ignores it (shows everything) — the same state serves both.
 */
internal enum class ServiceLogFilter { ALL, VERIFIED, FLAGGED }

/**
 * The fairness badge a single card shows — a **display** verdict with exactly the four
 * states the mockups render, so the composable never re-derives it from raw domain.
 * Mapped (in the ViewModel) from [VerificationStatus] + the domain `FairnessVerdict`:
 *  - [FairPrice]        — verified & within the city band ("Fair price")
 *  - [Overcharged]      — verified & over the city average ("Rs. 1,100 over")
 *  - [AddBillToVerify]  — self-reported, so no fairness check is possible yet
 *  - [NotYetChecked]    — verified, but no city benchmark for the category yet
 */
internal sealed interface ServiceLogFairnessBadge {
    data object FairPrice : ServiceLogFairnessBadge
    data class Overcharged(val by: Amount) : ServiceLogFairnessBadge
    data object AddBillToVerify : ServiceLogFairnessBadge
    data object NotYetChecked : ServiceLogFairnessBadge
}

/**
 * One row of the list — a display view of a service entry with exactly what a card
 * (ledger 1a) / timeline node (1b) needs, holding typed value objects ([Amount],
 * [Distance], [LocalDate]) rather than the raw `ServiceLogEntry`. The UI formats these
 * to strings; it never touches the domain aggregate.
 */
internal data class ServiceLogCardUiState(
    val id: ServiceLogId,
    val workshopName: String?,
    val serviceDate: LocalDate,
    val odometer: Distance,
    val amount: Amount,
    val verification: VerificationStatus,
    val categories: Set<ServiceCategory>,
    /** One-line "what was done", e.g. "Oil change + oil filter" (null if untagged). */
    val workDone: String?,
    val fairness: ServiceLogFairnessBadge,
)

/**
 * Service-log list render state. [content] is the mutually-exclusive load phase — a
 * sealed type, so "loading with a summary" or "empty with visible rows" can't be
 * represented and the UI's `when` is exhaustive. [filter] stays top-level (see above).
 */
internal data class ServiceLogListUiState(
    val content: Content = Content.Loading,
    val filter: ServiceLogFilter = ServiceLogFilter.ALL,
) {
    sealed interface Content {
        data object Loading : Content
        data object Empty : Content

        /**
         * A populated list. [cards] is the already-filtered/sorted rows the list
         * renders; the header stats span *all* entries:
         *  - [summary] powers both headers — 1a's "Total spent / N services" and 1b's
         *    record ring ("N of M verified", score, strength, resale uplift).
         *  - [savings] powers 1a's "Saved so far · N overcharges caught".
         *  - [flaggedCount] is the Flagged chip's count (verified entries judged over).
         */
        data class Loaded(
            val cards: List<ServiceLogCardUiState>,
            val summary: ServiceRecordSummary,
            val savings: FairnessSavings,
            val flaggedCount: Int,
        ) : Content {
            /** Verified-chip count. */
            val verifiedCount: Int get() = summary.verifiedCount
            /** Entries with no bill attached (the "Self-reported" share). */
            val selfReportedCount: Int get() = summary.serviceCount - summary.verifiedCount
        }
    }
}
