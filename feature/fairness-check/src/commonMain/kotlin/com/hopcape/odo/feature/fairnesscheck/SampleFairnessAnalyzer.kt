package com.hopcape.odo.feature.fairnesscheck

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.shared.Amount

/**
 * MVP stand-in for the real [FairnessAnalyzer] (which will read the server aggregate).
 * Uses a small canned table of city averages so the flow is demoable end-to-end; the
 * verdict math itself is the real domain [buildFairnessReport] / FairnessVerdict.of.
 */
internal class SampleFairnessAnalyzer : FairnessAnalyzer {
    override suspend fun analyze(query: FairnessQuery): FairnessReport {
        val lines = query.items.map { item ->
            BenchmarkLine(item.label, item.amount, cannedAverage(item.label) ?: item.amount)
        }
        return buildFairnessReport(city = query.city, sampleSize = SAMPLE_SIZE, lines = lines)
    }

    /** Canned city averages by job (else "fair" = the amount itself). Replaced by the RPC in M2. */
    private fun cannedAverage(label: String): Amount? = when (label.trim().lowercase()) {
        "oil change" -> rupees(2_100)
        "air filter" -> rupees(430)
        "labour" -> rupees(320)
        else -> null
    }

    private companion object {
        const val SAMPLE_SIZE = 12
        fun rupees(amount: Long): Amount = Amount.of(amount * 100).getOrElse { Amount.ZERO }
    }
}
