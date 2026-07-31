package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.model.FairnessSnapshot
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ObserveServiceLogFeedUseCaseTest {

    private val carId = CarId("car-1")

    private fun amt(paise: Long) = Amount.of(paise).getOrElse { Amount.ZERO }

    /** A stored verdict: [paidPaise] judged against [averagePaise] at check time. */
    private fun snapshot(paidPaise: Long, averagePaise: Long) = FairnessSnapshot(
        report = FairnessReport.of(
            query = FairnessQuery(
                city = "Pune",
                items = listOf(FairnessQueryItem(null, ServiceCategory.BRAKES, amt(paidPaise))),
            ),
            estimates = mapOf(
                ServiceCategory.BRAKES to FairnessEstimate(ServiceCategory.BRAKES, "Pune", amt(averagePaise), 30),
            ),
        ),
        checkedAt = Instant.parse("2026-07-03T10:00:00Z"),
    )

    private fun entry(
        id: String,
        day: Int,
        odometerKm: Int,
        totalPaise: Long = 330_000,
        verified: Boolean = true,
        fairness: FairnessSnapshot? = null,
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = carId,
        ownerId = OwnerId("owner-1"),
        serviceDate = LocalDate(2026, 6, day),
        odometerKm = odometerKm,
        totalAmountPaise = totalPaise,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = if (verified) BillId("bill-$id") else null,
        fairness = fairness,
    )

    @Test
    fun headerRowsAndChips_allComeFromOneRead() = runTest {
        val entries = listOf(
            entry("1", day = 1, odometerKm = 40_000, fairness = snapshot(330_000, 240_000)), // over by 90k
            entry("2", day = 10, odometerKm = 41_000, fairness = snapshot(240_000, 240_000)), // fair
            entry("3", day = 20, odometerKm = 42_000, verified = false), // self-reported
        )

        val feed = ObserveServiceLogFeedUseCase(FakeServiceLogRepository(entries)).invoke(carId).first()

        assertEquals(3, feed.entries.size)
        assertEquals(3, feed.summary.serviceCount)
        assertEquals(2, feed.verifiedCount)
        assertEquals(1, feed.flaggedCount)
        assertEquals(1, feed.savings.overchargesCaught)
        assertEquals(90_000L, feed.savings.overchargeTotal.paise)
        // The stat header spans every entry, flagged or not (3 × Rs. 3,300).
        assertEquals(990_000L, feed.summary.totalSpent.paise)
    }

    @Test
    fun withNoStoredVerdicts_thereAreNoSavings() = runTest {
        val entries = listOf(entry("1", day = 1, odometerKm = 40_000))

        val feed = ObserveServiceLogFeedUseCase(FakeServiceLogRepository(entries)).invoke(carId).first()

        assertEquals(0, feed.flaggedCount)
        assertEquals(0, feed.savings.overchargesCaught)
        assertEquals(0L, feed.savings.overchargeTotal.paise)
    }

    @Test
    fun overchargesAcrossEntries_areSummed() = runTest {
        val entries = listOf(
            entry("1", day = 1, odometerKm = 40_000, fairness = snapshot(330_000, 240_000)), // over by 90k
            entry("2", day = 10, odometerKm = 41_000, fairness = snapshot(300_000, 240_000)), // over by 60k
        )

        val feed = ObserveServiceLogFeedUseCase(FakeServiceLogRepository(entries)).invoke(carId).first()

        assertEquals(2, feed.flaggedCount)
        assertEquals(150_000L, feed.savings.overchargeTotal.paise)
    }

    @Test
    fun anEmptyLog_readsAsAnEmptySummary() = runTest {
        val feed = ObserveServiceLogFeedUseCase(FakeServiceLogRepository(emptyList())).invoke(carId).first()

        assertEquals(0, feed.entries.size)
        assertEquals(0, feed.summary.serviceCount)
        assertEquals(0, feed.flaggedCount)
        assertEquals(0, feed.savings.overchargesCaught)
    }

    @Test
    fun theFeedRefreshesWhenTheLogChanges() = runTest {
        val logs = FakeServiceLogRepository(listOf(entry("1", day = 1, odometerKm = 40_000)))
        val useCase = ObserveServiceLogFeedUseCase(logs)

        assertEquals(1, useCase(carId).first().summary.serviceCount)

        logs.add(entry("2", day = 10, odometerKm = 41_000, fairness = snapshot(330_000, 240_000)))

        val feed = useCase(carId).first()
        assertEquals(2, feed.summary.serviceCount)
        assertEquals(1, feed.flaggedCount)
    }
}
