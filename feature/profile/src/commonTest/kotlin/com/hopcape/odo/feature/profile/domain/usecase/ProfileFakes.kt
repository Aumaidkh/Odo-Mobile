package com.hopcape.odo.feature.profile.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.auth.VerifiedAccount
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.entitlement.ProEntitlement
import com.hopcape.odo.core.domain.owner.SessionStatusProvider
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import com.hopcape.odo.core.platform.file.PlatformFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal val TEST_OWNER = OwnerId("owner-1")

internal fun testProfile(
    name: String = "Rahul",
    goal: OnboardingGoal = OnboardingGoal.TRACK_COSTS,
): OwnerProfile = OwnerProfile.new(
    id = TEST_OWNER,
    name = OwnerName.of(name).getOrNull()!!,
    goal = goal,
)

internal fun testCar(id: String = "car-1"): Car = Car.create(
    id = CarId(id),
    ownerId = TEST_OWNER,
    make = "Maruti Suzuki",
    model = "Swift",
    year = 2019,
    fuelType = FuelType.PETROL,
    odometerKm = 54_000,
).getOrNull()!!

internal class FakeProfileRepository(
    profile: OwnerProfile? = testProfile(),
    var failing: Boolean = false,
    /**
     * Fails [delete] specifically.
     *
     * Separate from [failing], which only covers writes: the account-deletion flow needs a
     * wipe that fails *after* the server erase has already succeeded, and a repository that
     * refuses to save would never get that far.
     */
    var deleteFailing: Boolean = false,
) : OwnerProfileRepository {
    val stored = MutableStateFlow(profile)
    var deleteCount = 0

    override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> =
        if (failing) {
            DomainError.PersistenceFailure("disk full").left()
        } else {
            stored.value = profile
            profile.right()
        }

    override fun observe(): Flow<OwnerProfile?> = stored

    override suspend fun delete(): Either<DomainError, Unit> {
        deleteCount++
        if (deleteFailing) return DomainError.PersistenceFailure("disk full").left()
        stored.value = null
        return Unit.right()
    }
}

internal class FakeSettingsRepository(
    settings: AppSettings = AppSettings.Default,
    var failing: Boolean = false,
) : AppSettingsRepository {
    val stored = MutableStateFlow(settings)

    override fun observe(): Flow<AppSettings> = stored

    override suspend fun save(settings: AppSettings): Either<DomainError, AppSettings> =
        if (failing) {
            DomainError.PersistenceFailure("disk full").left()
        } else {
            stored.value = settings
            settings.right()
        }
}

internal class FakeCarRepository(car: Car? = testCar()) : CarRepository {
    val stored = MutableStateFlow(car)
    val softDeleted = mutableListOf<CarId>()

    override suspend fun add(car: Car): Either<DomainError, Car> = car.right()
    override suspend fun update(car: Car): Either<DomainError, Car> = car.right()
    override fun observePrimaryCar(): Flow<Car?> = stored
    override fun observe(id: CarId): Flow<Car?> = stored.map { car -> car?.takeIf { it.id == id } }

    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> {
        softDeleted += id
        stored.value = null
        return Unit.right()
    }
}

internal class FakeFileStore(var failing: Boolean = false) : PlatformFileStore {
    val saved = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override suspend fun save(
        pickedRef: String,
        directory: String,
        fileName: String,
    ): Either<DomainError, String> = if (failing) {
        DomainError.PersistenceFailure("no space").left()
    } else {
        "$directory/$fileName.jpg".also { saved += it }.right()
    }

    override suspend fun delete(storageKey: String) {
        deleted += storageKey
    }

    override suspend fun exists(storageKey: String): Boolean = storageKey in saved

    override suspend fun bytes(storageKey: String): Either<DomainError, ByteArray> =
        if (storageKey in saved) ByteArray(0).right() else DomainError.PersistenceFailure("gone").left()

    override suspend fun write(storageKey: String, bytes: ByteArray): Either<DomainError, String> {
        saved += storageKey
        return storageKey.right()
    }
}

/**
 * Only what [UpdatePrivacyUseCase] reaches for: the route purge, counted.
 *
 * Everything else throws rather than returning a benign default — this fake exists to prove
 * exactly one call happens or does not, and a silent no-op elsewhere would hide a use case
 * that started reading trips for some other reason.
 */
internal class FakeTripRepository(var failing: Boolean = false) : TripRepository {
    var forgetCount = 0
        private set

    override suspend fun forgetRoutes(): Either<DomainError, Unit> {
        forgetCount++
        return if (failing) DomainError.PersistenceFailure("disk full").left() else Unit.right()
    }

    override suspend fun add(trip: Trip): Either<DomainError, Trip> = throw NotImplementedError()
    override suspend fun addWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation): Either<DomainError, Trip> =
        throw NotImplementedError()
    override fun observe(carId: CarId): Flow<List<Trip>> = throw NotImplementedError()
    override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> = throw NotImplementedError()
    override suspend fun setStatus(id: TripId, status: TripStatus): Either<DomainError, Unit> =
        throw NotImplementedError()
    override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> = throw NotImplementedError()
    override suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip> =
        throw NotImplementedError()
    override suspend fun parkedLocation(carId: CarId): ParkedLocation? = throw NotImplementedError()
    override suspend fun deleteAllForCar(carId: CarId): Either<DomainError, Unit> = throw NotImplementedError()
}

internal fun entitlement(isPro: Boolean) = ProEntitlement { isPro }

internal fun session(signedIn: Boolean) = SessionStatusProvider { signedIn }

/** An auth provider holding [number], or nobody when null. [VerifiedAccount.delete] throws: only the read belongs here. */
internal fun account(number: PhoneNumber? = null) = object : VerifiedAccount {
    override suspend fun verifiedNumber(): PhoneNumber? = number
    override suspend fun delete() = throw NotImplementedError()
}
