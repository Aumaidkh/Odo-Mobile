package com.hopcape.odo.feature.servicelog.domain.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.FairnessVerdict
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
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
        override suspend fun estimate(category: ServiceCategory, city: String): FairnessEstimate? =
            table[category]?.let { (avg, sample) ->
                FairnessEstimate(category, city, Amount.of(avg).getOrElse { Amount.ZERO }, sample)
            }
    }

    private class FixedLogs(private val entries: List<ServiceLogEntry>) : ServiceLogRepository {
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(entries)
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(entries.find { it.id == id })
        override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
        override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
        override suspend fun mostRecentOdometerKm(carId: CarId): Int? = entries.maxOfOrNull { it.odometer.km }
    }

    private fun line(category: ServiceCategory, paise: Long) =
        ServiceLogLineItem.of(null, category, paise).getOrElse { error("bad line") }

    private fun entry(id: String, lines: List<ServiceLogLineItem>) = ServiceLogEntry.reconstitute(
        id = ServiceLogId(id),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = kotlinx.datetime.LocalDate(2026, 1, 1),
        odometerKm = 40_000,
        totalAmountPaise = lines.sumOf { it.amount.paise },
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = null,
        lineItems = lines,
    )

    @Test
    fun checkFairness_overCityAverage() = runTest {
        val useCase = CheckFairnessUseCase(FakeFairness(mapOf(ServiceCategory.BRAKES to (240_000L to 30))))
        val verdict = useCase(ServiceCategory.BRAKES, amt(330_000), "Pune")
        assertIs<FairnessVerdict.Over>(verdict)
        assertEquals(90_000L, verdict.by.paise)
    }

    @Test
    fun checkFairness_noBenchmark_isNull() = runTest {
        val useCase = CheckFairnessUseCase(FakeFairness(emptyMap()))
        assertNull(useCase(ServiceCategory.AC, amt(100_000), "Pune"))
    }

    @Test
    fun savings_sumsOnlyOverchargedLines() = runTest {
        val fairness = FakeFairness(
            mapOf(
                ServiceCategory.BRAKES to (240_000L to 30), // 330k paid → over by 90k
                ServiceCategory.OIL_CHANGE to (190_000L to 30), // 190k paid → fair
            ),
        )
        val logs = FixedLogs(
            listOf(
                entry(
                    "1",
                    listOf(
                        line(ServiceCategory.BRAKES, 330_000),
                        line(ServiceCategory.OIL_CHANGE, 190_000),
                    ),
                ),
            ),
        )
        val savings = ObserveFairnessSavingsUseCase(logs, fairness).invoke(CarId("car-1"), "Pune").first()
        assertEquals(1, savings.overchargesCaught)
        assertEquals(90_000L, savings.overchargeTotal.paise)
    }
}
