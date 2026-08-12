package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FairnessUseCasesTest {

    private fun amt(paise: Long) = Amount.of(paise).getOrElse { Amount.ZERO }

    /** Benchmarks keyed by category → (averagePaise, sampleSize). */
    private class FakeFairness(private val table: Map<ServiceCategory, Pair<Long, Int>>) : FairnessRepository {
        override suspend fun estimates(
            categories: Set<ServiceCategory>,
            city: String,
        ): Map<ServiceCategory, FairnessEstimate> = categories.mapNotNull { category ->
            table[category]?.let { (avg, sample) ->
                category to FairnessEstimate(category, city, Amount.of(avg).getOrElse { Amount.ZERO }, sample)
            }
        }.toMap()
    }

    private class FixedLogs(private val entries: List<ServiceLogEntry>) : ServiceLogRepository {
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(entries)
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(entries.find { it.id == id })
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun odometerReadings(carId: CarId): List<OdometerReading> =
            entries.map { OdometerReading(logId = it.id, date = it.serviceDate, odometer = it.odometer) }
        override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> =
            flowOf(entries.map { OdometerReading(logId = it.id, date = it.serviceDate, odometer = it.odometer) })
    }

    private fun line(category: ServiceCategory, paise: Long) =
        ServiceLogLineItem.of(null, category, paise).getOrElse { error("bad line") }

    /**
     * [verified] attaches a bill, which is what makes an entry eligible for a fairness
     * verdict at all (self-reported entries are never judged).
     */
    private fun entry(
        id: String,
        lines: List<ServiceLogLineItem>,
        verified: Boolean = true,
        categories: Set<ServiceCategory> = emptySet(),
        totalPaise: Long? = null,
    ) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = kotlinx.datetime.LocalDate(2026, 1, 1),
        odometerKm = 40_000,
        totalAmountPaise = totalPaise ?: lines.sumOf { it.amount.paise },
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = if (verified) BillId("bill-$id") else null,
        categories = categories,
        lineItems = lines,
    )

    @Test
    fun resolveEntryFairness_breaksDownEveryLine() = runTest {
        val fairness = FakeFairness(
            mapOf(
                ServiceCategory.BRAKES to (240_000L to 30), // 330k paid → over by 90k
                ServiceCategory.OIL_CHANGE to (190_000L to 30), // 190k paid → fair
            ),
        )
        val entry = entry(
            "1",
            listOf(line(ServiceCategory.BRAKES, 330_000), line(ServiceCategory.OIL_CHANGE, 190_000)),
        )

        val report = ResolveEntryFairnessUseCase(fairness)(entry, "Pune")!!

        assertEquals(2, report.items.size)
        val over = assertIs<FairnessOutcome.Over>(report.outcome)
        assertEquals(90_000L, over.by.paise)
        // The per-line city averages the detail screen's breakdown renders.
        assertEquals(240_000L, report.items.first { it.category == ServiceCategory.BRAKES }.cityAverage?.paise)
    }

    @Test
    fun resolveEntryFairness_fallsBackToTheEntrysSingleCategory() = runTest {
        // No priced breakdown: the whole total is judged against its one category.
        val fairness = FakeFairness(mapOf(ServiceCategory.BRAKES to (240_000L to 30)))
        val entry = entry(
            "1",
            lines = emptyList(),
            categories = setOf(ServiceCategory.BRAKES),
            totalPaise = 330_000,
        )

        val report = ResolveEntryFairnessUseCase(fairness)(entry, "Pune")!!

        assertEquals(1, report.items.size)
        assertIs<FairnessOutcome.Over>(report.outcome)
    }

    @Test
    fun resolveEntryFairness_selfReportedEntry_isNotJudged() = runTest {
        val fairness = FakeFairness(mapOf(ServiceCategory.BRAKES to (240_000L to 30)))
        val entry = entry("1", listOf(line(ServiceCategory.BRAKES, 330_000)), verified = false)

        assertNull(ResolveEntryFairnessUseCase(fairness)(entry, "Pune"))
    }

    @Test
    fun resolveEntryFairness_withoutACity_isNotJudged() = runTest {
        val fairness = FakeFairness(mapOf(ServiceCategory.BRAKES to (240_000L to 30)))
        val entry = entry("1", listOf(line(ServiceCategory.BRAKES, 330_000)))

        assertNull(ResolveEntryFairnessUseCase(fairness)(entry, city = null))
    }

    @Test
    fun resolveEntryFairness_multipleCategoriesOnOneTotal_isNotGuessedAt() = runTest {
        // Two tags, one amount — there is no honest way to split it.
        val fairness = FakeFairness(mapOf(ServiceCategory.BRAKES to (240_000L to 30)))
        val entry = entry(
            "1",
            lines = emptyList(),
            categories = setOf(ServiceCategory.BRAKES, ServiceCategory.OIL_CHANGE),
            totalPaise = 330_000,
        )

        assertNull(ResolveEntryFairnessUseCase(fairness)(entry, "Pune"))
    }
}
