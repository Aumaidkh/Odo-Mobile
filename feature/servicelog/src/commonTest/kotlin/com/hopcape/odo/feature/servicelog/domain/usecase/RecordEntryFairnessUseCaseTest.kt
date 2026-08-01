package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.servicelog.presentation.FakeServiceLogRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class RecordEntryFairnessUseCaseTest {

    private class FakeFairness(private val table: Map<ServiceCategory, Pair<Long, Int>>) : FairnessRepository {
        override suspend fun estimates(
            categories: Set<ServiceCategory>,
            city: String,
        ): Map<ServiceCategory, FairnessEstimate> = categories.mapNotNull { category ->
            table[category]?.let { (avg, sample) ->
                category to FairnessEstimate(category, city, amt(avg), sample)
            }
        }.toMap()
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val checkedAt = Instant.parse("2026-07-03T10:00:00Z")
    private val logId = ServiceLogId("log-1")

    private fun useCase(
        logs: FakeServiceLogRepository,
        benchmarks: Map<ServiceCategory, Pair<Long, Int>> = mapOf(ServiceCategory.BRAKES to (240_000L to 30)),
        city: String? = "Pune",
    ) = RecordEntryFairnessUseCase(
        logs = logs,
        resolveFairness = ResolveEntryFairnessUseCase(FakeFairness(benchmarks)),
        currentCity = CurrentCityProvider { city },
        clock = FixedClock(checkedAt),
    )

    /** A verified entry: one brakes line at Rs. 3,300, bill attached. */
    private fun verifiedEntry() = ServiceLogEntry.reconstitute(
        id = logId,
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = LocalDate(2026, 6, 1),
        odometerKm = 40_000,
        totalAmountPaise = 330_000,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = BillId("bill-1"),
        lineItems = listOf(
            ServiceLogLineItem.of(null, ServiceCategory.BRAKES, 330_000).getOrElse { error("bad line") },
        ),
    )

    @Test
    fun theVerdictIsStampedWithTheMomentItWasTaken() = runTest {
        val logs = FakeServiceLogRepository(listOf(verifiedEntry()))

        val entry = useCase(logs)(verifiedEntry()).getOrNull()

        val snapshot = assertNotNull(assertNotNull(entry).fairness)
        assertEquals(checkedAt, snapshot.checkedAt)
        val over = assertIs<FairnessOutcome.Over>(snapshot.outcome)
        assertEquals(90_000L, over.by.paise)
    }

    @Test
    fun theVerdictIsPersisted_notJustReturned() = runTest {
        val logs = FakeServiceLogRepository(listOf(verifiedEntry()))

        useCase(logs)(verifiedEntry())

        assertEquals(90_000L, logs.entries.value.single().fairness?.overchargedBy?.paise)
    }

    @Test
    fun withNoCity_theEntryIsLeftUnchanged() = runTest {
        val logs = FakeServiceLogRepository(listOf(verifiedEntry()))

        val entry = useCase(logs, city = null)(verifiedEntry()).getOrNull()

        assertNull(assertNotNull(entry).fairness, "no city means no benchmark — and no invented verdict")
        assertNull(logs.entries.value.single().fairness)
    }

    @Test
    fun withNoBenchmarkForTheCategory_theEntryIsLeftUnchanged() = runTest {
        val logs = FakeServiceLogRepository(listOf(verifiedEntry()))

        val entry = useCase(logs, benchmarks = emptyMap())(verifiedEntry()).getOrNull()

        // The report exists but judged nothing, so there is no verdict worth freezing.
        assertNull(assertNotNull(entry).fairness)
        assertNull(logs.entries.value.single().fairness)
    }

    @Test
    fun aSelfReportedEntry_isNeverJudged() = runTest {
        val selfReported = ServiceLogEntry.reconstitute(
            id = logId,
            carId = CarId("car-1"),
            ownerId = OwnerId("owner-1"),
            serviceDate = LocalDate(2026, 6, 1),
            odometerKm = 40_000,
            totalAmountPaise = 330_000,
            workshopName = null,
            notes = null,
            source = LogSource.MANUAL,
            billId = null,
            lineItems = listOf(
                ServiceLogLineItem.of(null, ServiceCategory.BRAKES, 330_000).getOrElse { error("bad line") },
            ),
        )
        val logs = FakeServiceLogRepository(listOf(selfReported))

        val entry = useCase(logs)(selfReported).getOrNull()

        assertNull(assertNotNull(entry).fairness)
    }

    private companion object {
        fun amt(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
    }
}
