package com.hopcape.odo.core.domain.servicelog.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceRecordScoreTest {

    private fun entry(id: String, km: Int, verified: Boolean) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = LocalDate(2026, 1, 1),
        odometerKm = km,
        totalAmountPaise = 0,
        workshopName = null,
        notes = null,
        source = if (verified) LogSource.SCANNED else LogSource.MANUAL,
        billId = if (verified) BillId("bill-$id") else null,
    )

    @Test
    fun empty_hasZeroScore_andNoResaleUplift() {
        val s = ServiceRecordSummary.of(emptyList())
        assertEquals(RecordScore.ZERO, s.score)
        assertNull(s.resaleUplift)
    }

    @Test
    fun fullyVerifiedAndCovered_scoresHigh() {
        // 6 services, all verified → verified 60 + coverage 40 = 100.
        val entries = (1..6).map { entry("v$it", km = 1_000 * it, verified = true) }
        val s = ServiceRecordSummary.of(entries)
        assertEquals(100, s.score.value)
        assertEquals(RecordStrength.STRONG, s.strength)
        // ~Rs. 5,000–8,000 per verified service, in paise.
        assertEquals(6 * 500_000L, s.resaleUplift?.low?.paise)
        assertEquals(6 * 800_000L, s.resaleUplift?.high?.paise)
    }

    @Test
    fun noneVerified_scoreFromCoverageOnly() {
        // 3 self-reported → verified 0 + coverage (3/6)*40 = 20.
        val entries = (1..3).map { entry("s$it", km = 1_000 * it, verified = false) }
        val s = ServiceRecordSummary.of(entries)
        assertEquals(20, s.score.value)
        assertNull(s.resaleUplift) // no verified entries
    }

    @Test
    fun score_isBounded() {
        val s = ServiceRecordSummary.of(listOf(entry("1", km = 1_000, verified = true)))
        assertTrue(s.score.value in 0..100)
    }
}
