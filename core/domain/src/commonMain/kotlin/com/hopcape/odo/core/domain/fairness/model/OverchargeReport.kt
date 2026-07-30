package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId

/**
 * Why the owner says a flagged service was an overcharge.
 *
 * Domain vocabulary, not a screen's radio group: the reason travels to the server, is
 * counted across owners, and decides whether a data point is worth keeping in the fairness
 * pool at all — [WORK_NOT_DONE] means the amount describes work that never happened, which
 * is very different evidence from a price simply being high.
 */
enum class OverchargeReason {
    /** The price was above what the job goes for. */
    ABOVE_MARKET_RATE,

    /** Billed for work that was not carried out. */
    WORK_NOT_DONE,

    /** Parts replaced that did not need replacing. */
    UNNECESSARY_PARTS,
}

/**
 * A user's report that a flagged service was an overcharge — the "Report this overcharge"
 * action. Feeds back into the fairness pool / support, keyed to the entry (and optionally
 * the specific [category] line) with the [reason] the owner gave.
 */
data class OverchargeReport(
    val logId: ServiceLogId,
    val reason: OverchargeReason,
    val category: ServiceCategory? = null,
    val note: String? = null,
)
