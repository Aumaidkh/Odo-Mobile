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
) {
    val confidence: FairnessConfidence get() = FairnessConfidence.of(sampleSize)
}
