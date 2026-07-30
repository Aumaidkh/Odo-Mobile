package com.hopcape.odo.core.domain.fairness.model

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FairnessReportTest {

    private fun paise(value: Long) = Amount.of(value).getOrElse { Amount.ZERO }

    private fun estimate(category: ServiceCategory, averagePaise: Long, sampleSize: Int = 30) =
        FairnessEstimate(category, CITY, paise(averagePaise), sampleSize)

    private fun query(vararg items: Pair<ServiceCategory?, Long>) = FairnessQuery(
        city = CITY,
        items = items.map { (category, amount) ->
            FairnessQueryItem(label = null, category = category, amount = paise(amount))
        },
    )

    @Test
    fun overchargedLine_makesTheWholeReportOver() {
        val report = FairnessReport.of(
            query(ServiceCategory.BRAKES to 330_000),
            mapOf(ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, 240_000)),
        )

        val over = assertIs<FairnessVerdict.Over>(report.overall)
        assertEquals(90_000L, over.by.paise)
    }

    @Test
    fun oneOverchargedLine_outweighsAFairTotal() {
        // Brakes are 90k over; the oil change is 85k under. The total lands inside the
        // fair band, but the owner was still overcharged on the brakes — which is the
        // whole point of the product.
        val report = FairnessReport.of(
            query(ServiceCategory.BRAKES to 330_000, ServiceCategory.OIL_CHANGE to 105_000),
            mapOf(
                ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, 240_000),
                ServiceCategory.OIL_CHANGE to estimate(ServiceCategory.OIL_CHANGE, 190_000),
            ),
        )

        val over = assertIs<FairnessVerdict.Over>(report.overall)
        assertEquals(90_000L, over.by.paise)
    }

    @Test
    fun overchargesAcrossLines_areSummed() {
        val report = FairnessReport.of(
            query(ServiceCategory.BRAKES to 330_000, ServiceCategory.AC to 200_000),
            mapOf(
                ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, 240_000),
                ServiceCategory.AC to estimate(ServiceCategory.AC, 100_000),
            ),
        )

        val over = assertIs<FairnessVerdict.Over>(report.overall)
        assertEquals(190_000L, over.by.paise)
    }

    @Test
    fun everyLineUnder_readsAsUnder() {
        val report = FairnessReport.of(
            query(ServiceCategory.BRAKES to 150_000),
            mapOf(ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, 240_000)),
        )

        val under = assertIs<FairnessVerdict.Under>(report.overall)
        assertEquals(90_000L, under.by.paise)
    }

    @Test
    fun withinTheTolerance_isFair() {
        val report = FairnessReport.of(
            query(ServiceCategory.BRAKES to 245_000),
            mapOf(ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, 240_000)),
        )

        assertIs<FairnessVerdict.Fair>(report.overall)
    }

    @Test
    fun thinlySampledBenchmark_isNotJudged() {
        // Fewer than 5 data points: the PRD forbids a confident verdict here.
        val report = FairnessReport.of(
            query(ServiceCategory.BRAKES to 330_000),
            mapOf(ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, 240_000, sampleSize = 3)),
        )

        assertIs<FairnessVerdict.LowConfidence>(report.overall)
        assertEquals(FairnessConfidence.LOW, report.confidence)
    }

    @Test
    fun unbenchmarkedLine_isCarriedThroughUnjudged() {
        val report = FairnessReport.of(query(ServiceCategory.BRAKES to 330_000), estimates = emptyMap())

        assertEquals(1, report.items.size)
        assertNull(report.items.single().verdict)
        assertNull(report.items.single().cityAverage)
        assertNull(report.overall)
    }

    @Test
    fun unbenchmarkedLine_doesNotDistortTheDifference() {
        // The uncategorised line contributes equally to both totals, so it moves the
        // comparison by nothing rather than looking like a Rs. 500 saving.
        val report = FairnessReport.of(
            query(ServiceCategory.BRAKES to 330_000, null to 50_000),
            mapOf(ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, 240_000)),
        )

        assertEquals(380_000L, report.yourTotal.paise)
        assertEquals(290_000L, report.cityAverageTotal.paise)
    }

    @Test
    fun sampleSize_isTheWeakestLine() {
        val report = FairnessReport.of(
            query(ServiceCategory.BRAKES to 330_000, ServiceCategory.AC to 200_000),
            mapOf(
                ServiceCategory.BRAKES to estimate(ServiceCategory.BRAKES, 240_000, sampleSize = 40),
                ServiceCategory.AC to estimate(ServiceCategory.AC, 190_000, sampleSize = 7),
            ),
        )

        assertEquals(7, report.sampleSize)
        assertEquals(FairnessConfidence.MEDIUM, report.confidence)
    }

    @Test
    fun emptyQuery_hasNoVerdictAndNoSample() {
        val report = FairnessReport.of(FairnessQuery(CITY, emptyList()), emptyMap())

        assertNull(report.overall)
        assertEquals(0, report.sampleSize)
        assertEquals(Amount.ZERO, report.yourTotal)
    }

    @Test
    fun categories_areWhatTheQueryNeedsBenchmarksFor() {
        val query = query(ServiceCategory.BRAKES to 1, ServiceCategory.BRAKES to 2, null to 3)

        assertEquals(setOf(ServiceCategory.BRAKES), query.categories)
    }

    private companion object {
        const val CITY = "Pune"
    }
}
