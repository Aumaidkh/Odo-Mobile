package com.hopcape.odo.core.domain.fairness.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount

/**
 * A city benchmark for one [ServiceCategory] — the average customers pay in [city],
 * plus the [sampleSize] it's based on. Read from the server's aggregate RPC (the raw
 * de-identified pool is never client-readable), so [sampleSize] always travels with the
 * average and drives the [confidence] the UI must surface.
 */
data class FairnessEstimate(
    val category: ServiceCategory,
    val city: String,
    val cityAverage: Amount,
    val sampleSize: Int,
    /**
     * What the middle of the pool actually paid. `null` when the source reported no
     * percentiles — an older stored snapshot, or a benchmark that only carries an average.
     */
    val range: FairnessRange? = null,
) {
    val confidence: FairnessConfidence get() = FairnessConfidence.of(sampleSize)
}

/**
 * The 25th to 75th percentile of the pool an estimate came from — what the middle half of
 * the city paid for this job.
 *
 * It exists so a thin sample can still say something true. Below the confidence floor there
 * is no verdict to give, and the PRD forbids dressing one up; a range that is really in the
 * data is honest in a way "the average, give or take some percent" is not.
 */
data class FairnessRange(
    val low: Amount,
    val high: Amount,
)
