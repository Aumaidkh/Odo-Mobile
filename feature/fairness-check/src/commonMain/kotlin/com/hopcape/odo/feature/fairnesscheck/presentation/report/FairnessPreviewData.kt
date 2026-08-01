package com.hopcape.odo.feature.fairnesscheck.presentation.report

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessRange
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount

/**
 * Sample states for the previews, built through the real [FairnessReport.of] and the real
 * mapper. A preview can therefore never show a verdict the domain would not produce — which
 * matters most for the two states this screen exists to get right: a thin sample and no
 * benchmark at all.
 */
internal fun sampleOverReport(): FairnessUiState.Content.Report = report(
    paid = listOf(
        ServiceCategory.OIL_CHANGE to 2_800L,
        ServiceCategory.GENERAL_SERVICE to 450L,
        ServiceCategory.BRAKES to 300L,
    ),
    canReport = true,
)

internal fun sampleFairReport(): FairnessUiState.Content.Report = report(
    paid = listOf(
        ServiceCategory.OIL_CHANGE to 2_180L,
        ServiceCategory.GENERAL_SERVICE to 430L,
        ServiceCategory.BRAKES to 300L,
    ),
)

/** Three data points in the city: enough to compare, nowhere near enough to judge. */
internal fun sampleThinReport(): FairnessUiState.Content.Report = report(
    paid = listOf(ServiceCategory.AC to 3_400L),
    sampleSize = 3,
)

/** A category the pool has never seen — the report carries the line through unjudged. */
internal fun sampleNoBenchmarkReport(): FairnessUiState.Content.Report =
    FairnessReport.of(
        FairnessQuery(
            city = CITY,
            items = listOf(FairnessQueryItem(label = "Rewiring", category = ServiceCategory.ELECTRICAL, amount = rupees(4_600))),
        ),
        estimates = emptyMap(),
    ).toContent(canReport = false)

private fun report(
    paid: List<Pair<ServiceCategory, Long>>,
    sampleSize: Int = 24,
    canReport: Boolean = false,
): FairnessUiState.Content.Report {
    val query = FairnessQuery(
        city = CITY,
        items = paid.map { (category, rupees) ->
            FairnessQueryItem(label = category.previewLabel(), category = category, amount = rupees(rupees))
        },
    )
    val estimates = paid.associate { (category, _) ->
        category to FairnessEstimate(
            category = category,
            city = CITY,
            cityAverage = rupees(AVERAGES.getValue(category)),
            sampleSize = sampleSize,
            range = FairnessRange(low = rupees(AVERAGES.getValue(category) - 300), high = rupees(AVERAGES.getValue(category) + 400)),
        )
    }
    return FairnessReport.of(query, estimates).toContent(canReport)
}

private const val CITY = "Pune"

private val AVERAGES = mapOf(
    ServiceCategory.OIL_CHANGE to 2_100L,
    ServiceCategory.GENERAL_SERVICE to 430L,
    ServiceCategory.BRAKES to 320L,
    ServiceCategory.AC to 2_600L,
)

/** Preview-only line names — real copy comes from the caller that built the query. */
private fun ServiceCategory.previewLabel(): String = when (this) {
    ServiceCategory.OIL_CHANGE -> "Oil change"
    ServiceCategory.GENERAL_SERVICE -> "Air filter"
    ServiceCategory.AC -> "AC gas refill"
    else -> "Labour"
}

private fun rupees(amount: Long): Amount = Amount.of(amount * 100).getOrElse { Amount.ZERO }
