package com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2

import com.hopcape.odo.core.domain.reminder.repository.DeclaredExpiryRepository
import com.hopcape.odo.core.domain.reminder.model.DeclaredExpiry
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.catalog.UnlistedVehicleReporter
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.setup.SetupDraft
import com.hopcape.odo.core.domain.setup.SetupDraftRepository
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.EngagementNudge
import com.hopcape.odo.core.platform.notification.EngagementNudgeScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock
import kotlin.time.Instant

internal class V2Catalog(var failing: Boolean = false) : VehicleCatalog {
    override suspend fun makes(): List<String> = read { listOf("Maruti Suzuki", "Hyundai") }
    override suspend fun popularMakes(limit: Int): List<String> = read { listOf("Maruti Suzuki", "Hyundai").take(limit) }
    override suspend fun models(make: String): List<CarModel> = read {
        if (make == "Hyundai") listOf(CarModel("Creta"), CarModel("Creta", "SX"), CarModel("Venue")) else emptyList()
    }
    override fun years(): List<Int> = (2026 downTo 2000).toList()
    override fun fuelTypes(): List<FuelType> = FuelType.entries

    private fun <T> read(value: () -> T): T = if (failing) error("catalog unavailable") else value()
}

internal class V2Cars(var failing: Boolean = false) : CarRepository {
    val added = mutableListOf<Car>()
    val updated = mutableListOf<Car>()

    override suspend fun add(car: Car): Either<DomainError, Car> =
        if (failing) DomainError.PersistenceFailure("disk full").left() else car.right().also { added += car }
    override suspend fun update(car: Car): Either<DomainError, Car> =
        if (failing) DomainError.PersistenceFailure("disk full").left() else car.right().also { updated += car }
    override fun observePrimaryCar(): Flow<Car?> = flowOf(added.lastOrNull())
    override fun observe(id: CarId): Flow<Car?> = flowOf(added.lastOrNull { it.id == id })
    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> = Unit.right()
}

internal class V2Logs : ServiceLogRepository {
    override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(emptyList())
    override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(null)
    override suspend fun add(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun update(entry: ServiceLogEntry): Either<DomainError, ServiceLogEntry> = entry.right()
    override suspend fun softDelete(id: ServiceLogId): Either<DomainError, Unit> = Unit.right()
    override suspend fun odometerReadings(carId: CarId): List<OdometerReading> = emptyList()
    override fun observeOdometerReadings(carId: CarId): Flow<List<OdometerReading>> = flowOf(emptyList())
}

internal class V2Nudges : EngagementNudgeScheduler {
    val scheduled = mutableListOf<EngagementNudge>()
    override suspend fun schedule(nudge: EngagementNudge) {
        scheduled += nudge
    }
}

internal class V2Unlisted : UnlistedVehicleReporter {
    val reports = mutableListOf<Triple<String, String, String?>>()
    override suspend fun report(make: String, model: String, variant: String?) {
        reports += Triple(make, model, variant)
    }
}

internal class V2Analytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    val names: List<String> get() = events.map { it.first }

    fun last(name: String): Map<String, Any?> = events.last { it.first == name }.second

    override fun track(eventName: String, properties: Map<String, Any?>) {
        events += eventName to properties
    }
    override fun identify(traits: UserTraits) = Unit
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}

internal class V2Drafts(var current: SetupDraft? = null) : SetupDraftRepository {
    val saves = mutableListOf<SetupDraft>()
    var clears = 0

    override fun observe(): Flow<SetupDraft?> = flowOf(current)
    override suspend fun save(draft: SetupDraft): Either<DomainError, Unit> {
        saves += draft
        current = draft
        return Unit.right()
    }
    override suspend fun clear(): Either<DomainError, Unit> {
        clears++
        current = null
        return Unit.right()
    }
}

internal class V2Clock(private val instant: Instant = Instant.parse("2026-10-24T12:00:00Z")) : Clock {
    override fun now(): Instant = instant
}

internal class V2DeclaredExpiries : DeclaredExpiryRepository {
    val declared = mutableListOf<DeclaredExpiry>()
    override fun observe(carId: CarId): Flow<List<DeclaredExpiry>> = flowOf(declared.toList())
    override suspend fun declare(expiry: DeclaredExpiry): Either<DomainError, Unit> {
        declared += expiry
        return Unit.right()
    }
}
