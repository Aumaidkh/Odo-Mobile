package com.hopcape.odo.core.domain.servicelog.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServiceRecordSummaryTest {

    private fun entry(
        id: String,
        km: Int,
        paise: Long,
        verified: Boolean,
        date: LocalDate = LocalDate(2026, 1, 1),
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = date,
        odometerKm = km,
        totalAmountPaise = paise,
        workshopName = null,
        notes = null,
        source = if (verified) LogSource.SCANNED else LogSource.MANUAL,
        billId = if (verified) BillId("bill-$id") else null,
    )

    @Test
    fun emptyList_isEmptySummary() {
        val summary = ServiceRecordSummary.of(emptyList())
        assertEquals(ServiceRecordSummary.EMPTY, summary)
        assertEquals(0, summary.serviceCount)
        assertEquals(0L, summary.totalSpent.paise)
        assertNull(summary.latestOdometer)
        assertEquals(0f, summary.verifiedRatio)
        assertEquals(RecordStrength.EMPTY, summary.strength)
    }

    @Test
    fun aggregatesCountsMoneyAndLatestOdometer() {
        val summary = ServiceRecordSummary.of(
            listOf(
                entry("1", km = 40_000, paise = 200_000, verified = true),
                entry("2", km = 54_000, paise = 320_000, verified = false),
            ),
        )
        assertEquals(2, summary.serviceCount)
        assertEquals(1, summary.verifiedCount)
        assertEquals(520_000L, summary.totalSpent.paise)
        assertEquals(54_000, summary.latestOdometer?.km) // same date → the higher reading wins
        assertEquals(0.5f, summary.verifiedRatio)
    }

    @Test
    fun latestOdometer_followsTheServiceDate_notTheHighestReading() {
        // Backdated history: the 2024 service is logged after the 2026 one. The car's
        // current reading is the one from the most recent service, not the largest number
        // in the list — which is only the same thing when nothing is ever backdated.
        val summary = ServiceRecordSummary.of(
            listOf(
                entry("recent", km = 41_000, paise = 0, verified = true, date = LocalDate(2026, 6, 1)),
                entry("old", km = 30_000, paise = 0, verified = false, date = LocalDate(2024, 3, 12)),
            ),
        )

        assertEquals(41_000, summary.latestOdometer?.km)
    }

    @Test
    fun strengthBands() {
        fun strengthFor(total: Int, verified: Int): RecordStrength =
            ServiceRecordSummary.of(
                List(verified) { entry("v$it", km = 1_000 + it, paise = 0, verified = true) } +
                    List(total - verified) { entry("s$it", km = 10_000 + it, paise = 0, verified = false) },
            ).strength

        assertEquals(RecordStrength.WEAK, strengthFor(total = 2, verified = 0))   // 0.0
        assertEquals(RecordStrength.FAIR, strengthFor(total = 3, verified = 1))   // 0.33
        assertEquals(RecordStrength.STRONG, strengthFor(total = 3, verified = 2)) // 0.66 → matches mockup 4/6
        assertEquals(RecordStrength.STRONG, strengthFor(total = 2, verified = 2)) // 1.0
    }
}
