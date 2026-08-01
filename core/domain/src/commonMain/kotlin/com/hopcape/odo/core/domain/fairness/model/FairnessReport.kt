package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.sum

/**
 * The output of a fairness check — a per-line comparison against city averages, plus the
 * headline [outcome] derived from it.
 *
 * Built only through [of], which is **the** fairness math for the whole app: a scanned
 * bill, a standalone price check and a logged service entry all reduce to a [FairnessQuery]
 * and come back through here, so no two surfaces can disagree about what "overcharged"
 * means. Everything but the city and the lines is derived — a report cannot drift out of
 * step with the items it was built from.
 */
data class FairnessReport(
    val city: String,
    val items: List<FairnessReportItem>,
) {
    /** What the owner actually paid, across every line — benchmarked or not. */
    val yourTotal: Amount get() = items.map { it.amount }.sum()

    /**
     * The comparable total. Lines with no benchmark contribute their own amount (treated
     * as "at average") rather than nothing, so an unpriceable line cannot masquerade as a
     * saving — it drops out of the difference instead of distorting it.
     */
    val cityAverageTotal: Amount get() = items.map { it.estimate?.cityAverage ?: it.amount }.sum()

    /**
     * The headline, merged from the lines.
     *
     * Any overcharged line makes the whole report [FairnessOutcome.Over] (by the summed
     * excess), because a total that lands inside the fair band can still hide one line that
     * is badly over — and catching exactly that is the product's job.
     *
     * The two ways of reaching no verdict stay apart: [FairnessOutcome.NoBenchmark] when
     * nothing here has a city average, [FairnessOutcome.TooLittleData] when the comparisons
     * exist but rest on too few data points. The UI has to say something different for each.
     */
    val outcome: FairnessOutcome
        get() {
            val verdicts = items.mapNotNull { it.verdict }
            if (verdicts.isEmpty()) return FairnessOutcome.NoBenchmark

            val over = verdicts.filterIsInstance<FairnessVerdict.Over>()
            if (over.isNotEmpty()) return FairnessOutcome.Over(over.map { it.by }.sum())
            if (verdicts.any { it is FairnessVerdict.Fair }) return FairnessOutcome.Fair

            val under = verdicts.filterIsInstance<FairnessVerdict.Under>()
            if (under.isNotEmpty()) return FairnessOutcome.Under(under.map { it.by }.sum())

            // Everything comparable was too thinly sampled to judge. The thinnest line
            // speaks for the report, for the same reason [sampleSize] takes the minimum.
            val thinnest = verdicts.filterIsInstance<FairnessVerdict.LowConfidence>()
                .minByOrNull { it.estimate.sampleSize }
            return thinnest?.let { FairnessOutcome.TooLittleData(it.estimate) }
                ?: FairnessOutcome.NoBenchmark
        }

    /**
     * The **weakest** evidence behind the report — the smallest sample of any benchmarked
     * line (`0` when nothing was benchmarked). The minimum rather than a sum or a mean,
     * because a report is only as trustworthy as its thinnest comparison and the PRD
     * forbids dressing that up (never false precision).
     */
    val sampleSize: Int get() = items.mapNotNull { it.estimate?.sampleSize }.minOrNull() ?: 0

    /** Confidence in [sampleSize] terms — what the UI must surface alongside any verdict. */
    val confidence: FairnessConfidence get() = FairnessConfidence.of(sampleSize)

    companion object {
        /**
         * Benchmark [query] against the city [estimates] available for its categories.
         *
         * A line whose category has no estimate (or which carries no category at all) is
         * kept in the report **unjudged** — a null verdict — so the UI can still show what
         * was paid without inventing a comparison for it.
         */
        fun of(
            query: FairnessQuery,
            estimates: Map<ServiceCategory, FairnessEstimate>,
        ): FairnessReport = FairnessReport(
            city = query.city,
            items = query.items.map { item ->
                val estimate = item.category?.let(estimates::get)
                FairnessReportItem(
                    label = item.label,
                    category = item.category,
                    amount = item.amount,
                    estimate = estimate,
                    verdict = estimate?.let { FairnessVerdict.of(item.amount, it) },
                )
            },
        )
    }
}

/**
 * One benchmarked line. [estimate] is `null` when there is no city average for it — which
 * is why [verdict] and [cityAverage] are nullable too: an unbenchmarked line is shown as
 * what it is, never quietly rendered as "fair".
 */
data class FairnessReportItem(
    val label: String?,
    val category: ServiceCategory?,
    val amount: Amount,
    val estimate: FairnessEstimate?,
    val verdict: FairnessVerdict?,
) {
    val cityAverage: Amount? get() = estimate?.cityAverage
}
