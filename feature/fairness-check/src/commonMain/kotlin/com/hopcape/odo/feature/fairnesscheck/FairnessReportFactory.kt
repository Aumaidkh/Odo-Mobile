package com.hopcape.odo.feature.fairnesscheck

import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessReportItem
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.sum

/** One benchmarked line: what was paid vs the city average for it. */
internal data class BenchmarkLine(val label: String, val amount: Amount, val cityAverage: Amount)

/**
 * Assemble a [FairnessReport] from benchmarked lines by running each — and the total —
 * through the domain's [FairnessVerdict.of]. Shared by the sample analyzer and previews
 * so the verdict math lives in exactly one place (and stays in the domain).
 */
internal fun buildFairnessReport(city: String, sampleSize: Int, lines: List<BenchmarkLine>): FairnessReport {
    fun verdict(actual: Amount, avg: Amount): FairnessVerdict =
        FairnessVerdict.of(actual, FairnessEstimate(ServiceCategory.OTHER, city, avg, sampleSize))

    val items = lines.map { FairnessReportItem(it.label, it.amount, it.cityAverage, verdict(it.amount, it.cityAverage)) }
    val yourTotal = lines.map { it.amount }.sum()
    val cityTotal = lines.map { it.cityAverage }.sum()
    return FairnessReport(city, sampleSize, yourTotal, cityTotal, verdict(yourTotal, cityTotal), items)
}
