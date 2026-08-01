package com.hopcape.odo.feature.fairnesscheck.presentation.report

import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessReportItem
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.shared.Amount

/**
 * The domain report as the screen's display state.
 *
 * [canReport] is decided here rather than in the composable: filing an overcharge report
 * needs both an overcharge and an entry to file it against, and that is a fact about the
 * data, not about the layout.
 */
internal fun FairnessReport.toContent(canReport: Boolean): FairnessUiState.Content.Report {
    val outcome = outcome
    return FairnessUiState.Content.Report(
        city = city,
        verdict = outcome.toVerdictUiState(difference = differenceFromAverage()),
        yourTotal = yourTotal,
        // Nothing benchmarked means nothing to compare against. Showing the two totals
        // would draw them equal, which reads as "exactly average" rather than "unknown".
        cityAverageTotal = cityAverageTotal.takeIf { outcome != FairnessOutcome.NoBenchmark },
        sampleSize = sampleSize,
        lines = items.map(FairnessReportItem::toLineUiState),
        canReport = canReport && outcome is FairnessOutcome.Over,
    )
}

/** How far the bill landed from the comparable total, as a positive amount. */
private fun FairnessReport.differenceFromAverage(): Amount =
    Amount.of(kotlin.math.abs(yourTotal.paise - cityAverageTotal.paise)).getOrNull() ?: Amount.ZERO

private fun FairnessOutcome.toVerdictUiState(difference: Amount): FairnessVerdictUiState = when (this) {
    is FairnessOutcome.Over -> FairnessVerdictUiState.Over(by)
    // Under the average is a fair price. The product flags overcharging; an owner who paid
    // less does not need a second, cleverer word for "you're fine".
    is FairnessOutcome.Under -> FairnessVerdictUiState.Fair(by)
    FairnessOutcome.Fair -> FairnessVerdictUiState.Fair(difference)
    is FairnessOutcome.TooLittleData ->
        FairnessVerdictUiState.TooLittleData(sampleSize = estimate.sampleSize, range = estimate.range)
    FairnessOutcome.NoBenchmark -> FairnessVerdictUiState.NoBenchmark
}

private fun FairnessReportItem.toLineUiState() = FairnessLineUiState(
    // The workshop's own words when there are any. A line logged by category alone has no
    // name the owner would recognise, and the screen supplies a generic one.
    label = label,
    paid = amount,
    cityAverage = cityAverage,
    verdict = verdict.toLineVerdictUiState(),
)

private fun FairnessVerdict?.toLineVerdictUiState(): FairnessLineVerdictUiState = when (this) {
    null -> FairnessLineVerdictUiState.NoBenchmark
    is FairnessVerdict.Over -> FairnessLineVerdictUiState.Over(by)
    is FairnessVerdict.LowConfidence -> FairnessLineVerdictUiState.TooLittleData
    FairnessVerdict.Fair, is FairnessVerdict.Under -> FairnessLineVerdictUiState.Fair
}
