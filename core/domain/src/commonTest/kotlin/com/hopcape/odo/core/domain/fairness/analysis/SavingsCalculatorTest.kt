package com.hopcape.odo.core.domain.fairness.analysis

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessReportItem
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SavingsCalculatorTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    private fun amount(paise: Long) = Amount.of(paise).getOrElse { error("test fixture paise=$paise") }

    private fun estimate(sampleSize: Int = 20) = FairnessEstimate(
        category = ServiceCategory.GENERAL_SERVICE,
        city = "Pune",
        cityAverage = amount(400_000),
        sampleSize = sampleSize,
    )

    private fun snapshot(verdict: FairnessVerdict) = FairnessSnapshot(
        report = FairnessReport(
            city = "Pune",
            items = listOf(
                FairnessReportItem(
                    label = "Full service",
                    category = ServiceCategory.GENERAL_SERVICE,
                    amount = amount(500_000),
                    estimate = estimate(),
                    verdict = verdict,
                ),
            ),
        ),
        checkedAt = Instant.parse("2026-07-01T10:00:00Z"),
    )

    private fun entry(id: String, fairness: FairnessSnapshot?) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = ownerId,
        serviceDate = LocalDate(2026, 6, 1),
        odometerKm = 50_000,
        totalAmountPaise = 500_000,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = null,
        fairness = fairness,
    )

    @Test
    fun `no entries means nothing caught`() {
        val savings = SavingsCalculator.of(emptyList())

        assertEquals(Amount.ZERO, savings.overchargeTotal)
        assertEquals(0, savings.overchargesCaught)
    }

    @Test
    fun `sums every overcharge and counts the entries it came from`() {
        val entries = listOf(
            entry("a", snapshot(FairnessVerdict.Over(amount(70_000)))),
            entry("b", snapshot(FairnessVerdict.Over(amount(140_000)))),
        )

        val savings = SavingsCalculator.of(entries)

        assertEquals(amount(210_000), savings.overchargeTotal)
        assertEquals(2, savings.overchargesCaught)
    }

    /** A thin sample is not evidence against the owner, so it must not be counted as caught. */
    @Test
    fun `fair, under, thinly sampled and unchecked entries contribute nothing`() {
        val entries = listOf(
            entry("fair", snapshot(FairnessVerdict.Fair)),
            entry("under", snapshot(FairnessVerdict.Under(amount(50_000)))),
            entry("thin", snapshot(FairnessVerdict.LowConfidence(estimate(sampleSize = 2)))),
            entry("unchecked", fairness = null),
        )

        val savings = SavingsCalculator.of(entries)

        assertEquals(Amount.ZERO, savings.overchargeTotal)
        assertEquals(0, savings.overchargesCaught)
    }

    @Test
    fun `one overcharge among many fair bills is still caught`() {
        val entries = listOf(
            entry("fair", snapshot(FairnessVerdict.Fair)),
            entry("over", snapshot(FairnessVerdict.Over(amount(90_000)))),
            entry("unchecked", fairness = null),
        )

        val savings = SavingsCalculator.of(entries)

        assertEquals(amount(90_000), savings.overchargeTotal)
        assertEquals(1, savings.overchargesCaught)
    }
}
