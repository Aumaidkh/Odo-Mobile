package com.hopcape.odo.core.domain.insight.analysis

import com.hopcape.odo.core.domain.cost.model.CostTrend
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.latestOfType
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.insight.model.CarInsight
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification

/**
 * Picks the one thing worth saying about a car's record that is not a deadline.
 *
 * A pure domain service — no repository, no clock. It reads the car's logs, its papers and
 * a cost trend someone else computed, and returns a single [CarInsight] or `null` when the
 * record says nothing interesting yet.
 *
 * **Most specific rule first.** A rare condition ranked below a common one never surfaces:
 * a car whose every service is verified would also have a cost trend, and would only ever
 * be told about the trend. So the order runs from the narrowest condition to the widest,
 * not from what feels most important.
 *
 * Nothing here restates a deadline. The service interval and expiring papers belong to
 * [AttentionPicker][com.hopcape.odo.core.domain.alerts.analysis.AttentionPicker], which
 * Home renders directly above this — two cards saying the same thing is worse than one.
 */
object InsightPicker {

    /**
     * The shortest record that can call itself resale-ready.
     *
     * One verified service is a receipt, not a history, and a buyer looking at a single
     * bill is not looking at a maintained car.
     */
    const val MIN_SERVICES_FOR_RESALE: Int = 3

    /**
     * How far the running cost must have moved before it is worth a line.
     *
     * Below this the change is mostly which month a service happened to land in, and
     * reporting it would train owners to ignore the card.
     */
    const val MIN_TREND_PERCENT: Int = 10

    /**
     * The insight for a car as it stands, or `null` when its record says nothing yet.
     *
     * @param entries every non-deleted service log for the car.
     * @param documents every non-deleted document for the car.
     * @param costTrend how ₹/km moved against the window before, from
     *   [RunningCost.trendAgainst][com.hopcape.odo.core.domain.cost.model.RunningCost.trendAgainst];
     *   `null` when either window had no rate to compare.
     */
    fun pick(
        entries: List<ServiceLogEntry>,
        documents: List<Document>,
        costTrend: CostTrend?,
    ): CarInsight? = resaleReady(entries)
        ?: costMoved(costTrend)
        ?: noBillsAttached(entries)
        ?: documentMissing(documents)

    private fun resaleReady(entries: List<ServiceLogEntry>): CarInsight? {
        if (entries.size < MIN_SERVICES_FOR_RESALE) return null
        if (entries.any { it.verification != VerificationStatus.VERIFIED }) return null
        return CarInsight.ResaleReady(serviceCount = entries.size)
    }

    private fun costMoved(trend: CostTrend?): CarInsight? {
        if (trend == null || trend.magnitude < MIN_TREND_PERCENT) return null
        return CarInsight.CostMoved(percentChange = trend.percentChange)
    }

    private fun noBillsAttached(entries: List<ServiceLogEntry>): CarInsight? {
        if (entries.isEmpty()) return null
        if (entries.any { it.verification == VerificationStatus.VERIFIED }) return null
        return CarInsight.NoBillsAttached(serviceCount = entries.size)
    }

    /**
     * The heaviest scored paper the vault has no copy of at all.
     *
     * Absent, not lapsed: a paper that has run out is already the attention card's line,
     * and repeating it here would take the one slot an insight has.
     */
    private fun documentMissing(documents: List<Document>): CarInsight? =
        HealthScoreCalculator.SCORED_DOCUMENT_TYPES
            .firstOrNull { documents.latestOfType(it) == null }
            ?.let { CarInsight.DocumentMissing(type = it) }
}
