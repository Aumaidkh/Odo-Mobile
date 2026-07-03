package com.hopcape.odo.feature.servicelog.presentation.list

import com.hopcape.odo.core.domain.fairness.model.FairnessSavings
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceRecordSummary

/** A ledger card: the entry plus its resolved fairness verdict (null = self-reported / no benchmark). */
internal data class LedgerRow(
    val entry: ServiceLogEntry,
    val fairness: FairnessVerdict?,
)

/**
 * Ledger render state. [content] is the mutually-exclusive load phase — a sealed type, so
 * "loading with a summary" or "empty with visible rows" can't be represented and the UI's
 * `when` is exhaustive. [filter] is an orthogonal control that stays top-level: it survives
 * content re-emits and is meaningful even before the first load.
 */
internal data class ServiceLogListUiState(
    val content: Content = Content.Loading,
    val filter: ServiceLogFilter = ServiceLogFilter.ALL,
) {
    sealed interface Content {
        data object Loading : Content
        data object Empty : Content

        /**
         * [summary]/[savings]/[flaggedCount] are over *all* entries (the header + chip
         * counts); [visible] is the filtered rows the list renders.
         */
        data class Ledger(
            val summary: ServiceRecordSummary,
            val savings: FairnessSavings,
            val flaggedCount: Int,
            val visible: List<LedgerRow>,
        ) : Content {
            val selfReportedCount: Int get() = summary.serviceCount - summary.verifiedCount
        }
    }
}
