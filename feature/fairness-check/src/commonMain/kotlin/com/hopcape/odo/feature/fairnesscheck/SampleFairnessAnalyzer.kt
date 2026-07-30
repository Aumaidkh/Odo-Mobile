package com.hopcape.odo.feature.fairnesscheck

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount

/**
 * MVP stand-in for the real [FairnessAnalyzer] (which reads the server aggregate through
 * `FairnessRepository`). It supplies a small canned table of city averages so the flow is
 * demoable end-to-end — the verdict math itself is the real domain [FairnessReport.of],
 * exactly what the RPC-backed adapter will call. Swapping this out changes where the
 * numbers come from, never what they mean.
 */
internal class SampleFairnessAnalyzer : FairnessAnalyzer {
    override suspend fun analyze(query: FairnessQuery): FairnessReport =
        FairnessReport.of(query, cannedEstimates(query))

    /** Canned city averages per category. Replaced by the `get_fairness_estimate` RPC in M2. */
    private fun cannedEstimates(query: FairnessQuery): Map<ServiceCategory, FairnessEstimate> =
        query.categories.mapNotNull { category ->
            cannedAverage(category)?.let { average ->
                category to FairnessEstimate(
                    category = category,
                    city = query.city,
                    cityAverage = average,
                    sampleSize = SAMPLE_SIZE,
                )
            }
        }.toMap()

    private fun cannedAverage(category: ServiceCategory): Amount? = when (category) {
        ServiceCategory.OIL_CHANGE -> rupees(2_100)
        ServiceCategory.BRAKES -> rupees(3_400)
        ServiceCategory.GENERAL_SERVICE -> rupees(4_200)
        ServiceCategory.AC -> rupees(2_600)
        ServiceCategory.BATTERY -> rupees(5_800)
        // No canned benchmark — the report carries these lines through unjudged rather
        // than inventing an average for them.
        else -> null
    }

    private companion object {
        const val SAMPLE_SIZE = 12
        fun rupees(amount: Long): Amount = Amount.of(amount * 100).getOrElse { Amount.ZERO }
    }
}
