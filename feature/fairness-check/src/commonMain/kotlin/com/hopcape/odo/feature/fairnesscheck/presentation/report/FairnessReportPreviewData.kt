package com.hopcape.odo.feature.fairnesscheck.presentation.report

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.fairnesscheck.BenchmarkLine
import com.hopcape.odo.feature.fairnesscheck.buildFairnessReport

/** Sample reports for previews — overcharge (Rs. 3,550 vs 2,850) and fair (within Rs. 60). */
internal fun sampleFairnessReport(over: Boolean): FairnessReport = buildFairnessReport(
    city = "Pune",
    sampleSize = 12,
    lines = if (over) {
        listOf(
            BenchmarkLine("Oil change", rupees(2_800), rupees(2_100)),
            BenchmarkLine("Air filter", rupees(450), rupees(430)),
            BenchmarkLine("Labour", rupees(300), rupees(320)),
        )
    } else {
        listOf(
            BenchmarkLine("Oil change", rupees(2_180), rupees(2_100)),
            BenchmarkLine("Air filter", rupees(430), rupees(430)),
            BenchmarkLine("Labour", rupees(300), rupees(320)),
        )
    },
)

private fun rupees(amount: Long): Amount = Amount.of(amount * 100).getOrElse { Amount.ZERO }
