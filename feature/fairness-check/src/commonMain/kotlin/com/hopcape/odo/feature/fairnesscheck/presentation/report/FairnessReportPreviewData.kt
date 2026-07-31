package com.hopcape.odo.feature.fairnesscheck.presentation.report

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount

/**
 * Sample reports for previews — overcharge (Rs. 3,550 paid against a Rs. 2,850 benchmark)
 * and fair (within Rs. 60). Built through the real [FairnessReport.of], so a preview can
 * never show a verdict the domain would not produce.
 */
internal fun sampleFairnessReport(over: Boolean): FairnessReport {
    val paid = if (over) {
        listOf(
            ServiceCategory.OIL_CHANGE to rupees(2_800),
            ServiceCategory.GENERAL_SERVICE to rupees(450),
            ServiceCategory.BRAKES to rupees(300),
        )
    } else {
        listOf(
            ServiceCategory.OIL_CHANGE to rupees(2_180),
            ServiceCategory.GENERAL_SERVICE to rupees(430),
            ServiceCategory.BRAKES to rupees(300),
        )
    }
    val query = FairnessQuery(
        city = CITY,
        items = paid.map { (category, amount) ->
            FairnessQueryItem(label = category.previewLabel(), category = category, amount = amount)
        },
    )
    return FairnessReport.of(query, PREVIEW_AVERAGES)
}

private const val CITY = "Pune"
private const val SAMPLE_SIZE = 12

private val PREVIEW_AVERAGES: Map<ServiceCategory, FairnessEstimate> = mapOf(
    ServiceCategory.OIL_CHANGE to estimate(ServiceCategory.OIL_CHANGE, rupees(2_100)),
    ServiceCategory.GENERAL_SERVICE to estimate(ServiceCategory.GENERAL_SERVICE, rupees(430)),
    ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, rupees(320)),
)

private fun estimate(category: ServiceCategory, average: Amount) =
    FairnessEstimate(category = category, city = CITY, cityAverage = average, sampleSize = SAMPLE_SIZE)

/** Preview-only line names — real copy comes from the caller that built the query. */
private fun ServiceCategory.previewLabel(): String = when (this) {
    ServiceCategory.OIL_CHANGE -> "Oil change"
    ServiceCategory.GENERAL_SERVICE -> "Air filter"
    else -> "Labour"
}

private fun rupees(amount: Long): Amount = Amount.of(amount * 100).getOrElse { Amount.ZERO }
