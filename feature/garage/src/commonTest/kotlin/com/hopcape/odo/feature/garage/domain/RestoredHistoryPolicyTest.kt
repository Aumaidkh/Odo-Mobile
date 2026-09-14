package com.hopcape.odo.feature.garage.domain

import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.feature.garage.domain.usecase.TEST_OWNER
import com.hopcape.odo.feature.garage.domain.usecase.testCar
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the owner is told after signing in restored their car's history (issue #425), and
 * the one thing the sheet must never offer to do.
 */
class RestoredHistoryPolicyTest {

    @Test
    fun aCarWhoseHistoryCameBackIsAnnouncedWithWhatItHolds() {
        val summary = RestoredHistoryPolicy.summarise(
            car = testCar(odometerKm = 45_000),
            logs = listOf(log("log-1", date = LocalDate(2024, 3, 8), km = 30_000), log("log-2", km = 44_000)),
            documents = emptyList(),
            fills = emptyList(),
        )

        assertEquals(2, summary?.serviceLogCount)
        // The oldest record is what "since" means — how far back the account goes.
        assertEquals(LocalDate(2024, 3, 8), summary?.since)
        assertEquals("Maruti Suzuki Swift VXI", summary?.carName)
        assertEquals("MH12AB1234", summary?.registrationNumber)
    }

    @Test
    fun aRestoreWithNothingInItIsNotAnnounced() {
        val summary = RestoredHistoryPolicy.summarise(
            car = testCar(),
            logs = emptyList(),
            documents = emptyList(),
            fills = emptyList(),
        )

        // The car came down from the server but carries no records. There is nothing to
        // celebrate and a sheet saying so would be noise.
        assertNull(summary)
    }

    @Test
    fun aHistoryReadingAboveTheCarsOwnIsOfferedAsACorrection() {
        val summary = RestoredHistoryPolicy.summarise(
            car = testCar(odometerKm = 12_000),
            logs = listOf(log("log-1", km = 78_450)),
            documents = emptyList(),
            fills = emptyList(),
        )

        assertEquals(12_000, summary?.odometer?.currentKm)
        assertEquals(78_450, summary?.odometer?.historyKm)
    }

    @Test
    fun aHistoryReadingBelowTheCarsOwnIsNeverOffered() {
        val summary = RestoredHistoryPolicy.summarise(
            car = testCar(odometerKm = 92_000),
            logs = listOf(log("log-1", km = 78_450)),
            documents = emptyList(),
            fills = emptyList(),
        )

        // The whole rule in one assertion. An odometer that counts down corrupts every
        // per-km figure built on it, so the restore may raise the reading and nothing else.
        assertNull(summary?.odometer)
        assertEquals(1, summary?.serviceLogCount, "the restore is still announced")
    }

    @Test
    fun aHistoryReadingEqualToTheCarsOwnIsNotACorrection() {
        val summary = RestoredHistoryPolicy.summarise(
            car = testCar(odometerKm = 78_450),
            logs = listOf(log("log-1", km = 78_450)),
            documents = emptyList(),
            fills = emptyList(),
        )

        // Nothing to put right, so there is no question to ask.
        assertNull(summary?.odometer)
    }

    private fun log(id: String, date: LocalDate = LocalDate(2026, 1, 15), km: Int) =
        ServiceLogEntry.reconstitute(
            id = ServiceLogId(id),
            carId = testCar().id,
            ownerId = TEST_OWNER,
            serviceDate = date,
            odometerKm = km,
            totalAmountPaise = 280_000,
            workshopName = "Sharma Motors",
            notes = null,
            source = LogSource.MANUAL,
            billId = null,
        )
}
