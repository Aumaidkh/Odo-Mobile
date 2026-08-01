package com.hopcape.odo.feature.fairnesscheck.presentation.report

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessRange
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FairnessMapperTest {

    @Test
    fun anOverchargeCarriesTheAmountAndAllowsAReport() {
        val content = report(paid = 330_000, average = 240_000).toContent(canReport = true)

        val over = assertIs<FairnessVerdictUiState.Over>(content.verdict)
        assertEquals(90_000L, over.by.paise)
        assertTrue(content.canReport)
    }

    @Test
    fun withoutTheEntryIds_thereIsNothingToReportAgainst() {
        val content = report(paid = 330_000, average = 240_000).toContent(canReport = false)

        assertIs<FairnessVerdictUiState.Over>(content.verdict)
        assertFalse(content.canReport)
    }

    @Test
    fun aFairBill_neverOffersTheReportAction() {
        val content = report(paid = 245_000, average = 240_000).toContent(canReport = true)

        assertIs<FairnessVerdictUiState.Fair>(content.verdict)
        assertFalse(content.canReport, "there is no overcharge to report")
    }

    @Test
    fun payingUnderTheAverage_readsAsFair() {
        val content = report(paid = 150_000, average = 240_000).toContent(canReport = false)

        val fair = assertIs<FairnessVerdictUiState.Fair>(content.verdict)
        assertEquals(90_000L, fair.difference.paise)
    }

    @Test
    fun aThinSample_carriesItsRangeAndNoVerdict() {
        val content = report(paid = 330_000, average = 240_000, sampleSize = 3).toContent(canReport = true)

        val thin = assertIs<FairnessVerdictUiState.TooLittleData>(content.verdict)
        assertEquals(3, thin.sampleSize)
        assertEquals(200_000L, thin.range?.low?.paise)
        assertEquals(290_000L, thin.range?.high?.paise)
        assertFalse(content.canReport, "a pool this thin cannot support an overcharge claim")
    }

    @Test
    fun withNoBenchmark_thereIsNoComparisonToDraw() {
        val content = FairnessReport.of(query(330_000), estimates = emptyMap()).toContent(canReport = true)

        assertEquals(FairnessVerdictUiState.NoBenchmark, content.verdict)
        // Equal-height bars would read as "you paid exactly the city average".
        assertNull(content.cityAverageTotal)
        assertEquals(0, content.sampleSize)
        assertFalse(content.canReport)
    }

    @Test
    fun everyLineIsCarriedThrough_judgedOrNot() {
        val report = FairnessReport.of(
            FairnessQuery(
                city = CITY,
                items = listOf(
                    FairnessQueryItem("Front pads", ServiceCategory.BRAKES, paise(330_000)),
                    FairnessQueryItem("Sundries", null, paise(50_000)),
                ),
            ),
            mapOf(ServiceCategory.BRAKES to estimate(240_000, sampleSize = 24)),
        )

        val lines = report.toContent(canReport = false).lines

        assertEquals(2, lines.size)
        assertIs<FairnessLineVerdictUiState.Over>(lines.first().verdict)
        assertEquals(FairnessLineVerdictUiState.NoBenchmark, lines.last().verdict)
        assertNull(lines.last().cityAverage)
    }

    @Test
    fun aThinlySampledLine_neverBorrowsTheFairLabel() {
        val lines = report(paid = 330_000, average = 240_000, sampleSize = 2).toContent(canReport = false).lines

        assertEquals(FairnessLineVerdictUiState.TooLittleData, lines.single().verdict)
    }

    /* ------------------------- fixtures ------------------------- */

    private fun report(paid: Long, average: Long, sampleSize: Int = 24) =
        FairnessReport.of(query(paid), mapOf(ServiceCategory.BRAKES to estimate(average, sampleSize)))

    private fun query(paid: Long) = FairnessQuery(
        city = CITY,
        items = listOf(FairnessQueryItem("Front pads", ServiceCategory.BRAKES, paise(paid))),
    )

    private fun estimate(average: Long, sampleSize: Int) = FairnessEstimate(
        category = ServiceCategory.BRAKES,
        city = CITY,
        cityAverage = paise(average),
        sampleSize = sampleSize,
        range = FairnessRange(low = paise(average - 40_000), high = paise(average + 50_000)),
    )

    private fun paise(value: Long): Amount = Amount.of(value).getOrElse { Amount.ZERO }

    private companion object {
        const val CITY = "Pune"
    }
}
