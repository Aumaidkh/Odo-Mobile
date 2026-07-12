package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.shared.Amount

/**
 * The output of a fairness check — the overall verdict plus a per-item breakdown, each
 * against its city average, with the [sampleSize] the benchmark rests on (so the UI can
 * surface confidence honestly, never false precision).
 */
data class FairnessReport(
    val city: String,
    val sampleSize: Int,
    val yourTotal: Amount,
    val cityAverageTotal: Amount,
    val overall: FairnessVerdict,
    val items: List<FairnessReportItem>,
)

data class FairnessReportItem(
    val label: String,
    val amount: Amount,
    val cityAverage: Amount,
    val verdict: FairnessVerdict,
)
