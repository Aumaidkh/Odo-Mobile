package com.hopcape.odo.feature.servicelog.presentation

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.fairness.repository.OverchargeReportRepository
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.servicelog.domain.usecase.ResolveEntryFairnessUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * In-memory [ServiceLogRepository]. [carBaselineKm] emulates the car's onboarding reading
 * (`null` = no such car), dated [carBaselineDate] so it takes its place on the timeline.
 */
internal class FakeServiceLogRepository(
    initial: List<ServiceLogEntry> = emptyList(),
    private val carBaselineKm: Int? = 0,
    private val carBaselineDate: LocalDate = LocalDate(2020, 1, 1),
) : ServiceLogRepository {

    val entries = MutableStateFlow(initial)
    var addCount = 0
    var deleteCount = 0

    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> =
        entries.map { list -> list.filter { it.carId == carId }.sortedByDescending { it.serviceDate } }

    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> =
        entries.map { list -> list.find { it.id == id } }

    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> {
        addCount++
        entries.update { it + entry }
        return entry.right()
    }

    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> {
        entries.update { list -> list.map { if (it.id == entry.id) entry else it } }
        return entry.right()
    }

    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> {
        deleteCount++
        entries.update { list -> list.filterNot { it.id == id } }
        return Unit.right()
    }

    override suspend fun odometerReadings(carId: CarId): List<OdometerReading>? {
        val baselineKm = carBaselineKm ?: return null
        val baseline = OdometerReading(
            logId = null,
            date = carBaselineDate,
            odometer = Distance.of(baselineKm).getOrElse { error("test baseline km=$baselineKm") },
        )
        val logs = entries.value
            .filter { it.carId == carId }
            .map { OdometerReading(logId = it.id, date = it.serviceDate, odometer = it.odometer) }
        return listOf(baseline) + logs
    }
}

internal object NoopLogger : Logger {
    override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) {}
    override fun flush() {}
}

internal object NoopAnalytics : AnalyticsTracker {
    override fun identify(traits: UserTraits) {}
    override fun track(eventName: String, properties: Map<String, Any?>) {}
    override fun setConsent(status: ConsentStatus) {}
    override fun flush() {}
}

internal class FixedIdGenerator(private val id: String = "log-new") : IdGenerator {
    override fun newId(): String = id
}

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** 2026-07-03 in UTC — the fixed "today" for date-guard tests. */
internal val TEST_CLOCK = FixedClock(Instant.parse("2026-07-03T10:00:00Z"))
internal val TEST_CAR = CarId("car-1")
internal val TEST_OWNER = OwnerId("owner-1")

internal fun testEntry(
    id: String,
    km: Int,
    paise: Long = 0,
    verified: Boolean = false,
    date: LocalDate = LocalDate(2026, 1, 1),
    workshop: String? = "Sharma Motors",
    notes: String? = null,
    categories: Set<ServiceCategory> = emptySet(),
): ServiceLogEntry = ServiceLogEntry.reconstitute(
    id = ServiceLogId(id),
    carId = TEST_CAR,
    ownerId = TEST_OWNER,
    serviceDate = date,
    odometerKm = km,
    totalAmountPaise = paise,
    workshopName = workshop,
    notes = notes,
    source = if (verified) LogSource.SCANNED else LogSource.MANUAL,
    billId = if (verified) BillId("bill-$id") else null,
    categories = categories,
)

/** The user's city for tests. */
internal val TEST_CITY = CurrentCityProvider { "Pune" }

/** Benchmarks keyed by category → (averagePaise, sampleSize); empty = no verdicts. */
internal class FakeFairnessRepository(
    private val table: Map<ServiceCategory, Pair<Long, Int>> = emptyMap(),
) : FairnessRepository {
    override suspend fun estimates(
        categories: Set<ServiceCategory>,
        city: String,
    ): Map<ServiceCategory, FairnessEstimate> = categories.mapNotNull { category ->
        table[category]?.let { (avg, sample) ->
            category to FairnessEstimate(category, city, Amount.of(avg).getOrElse { Amount.ZERO }, sample)
        }
    }.toMap()
}

internal class FakeOverchargeReportRepository : OverchargeReportRepository {
    var submitted: OverchargeReport? = null
    override suspend fun submit(report: OverchargeReport): Either<DomainError, Unit> {
        submitted = report
        return Unit.right()
    }
}

/** A [ResolveEntryFairnessUseCase] over an optional benchmark table. */
internal fun testResolveFairness(table: Map<ServiceCategory, Pair<Long, Int>> = emptyMap()) =
    ResolveEntryFairnessUseCase(FakeFairnessRepository(table))
