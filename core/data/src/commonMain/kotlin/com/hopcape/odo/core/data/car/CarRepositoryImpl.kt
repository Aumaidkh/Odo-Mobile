package com.hopcape.odo.core.data.car

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * SQLDelight-backed [CarRepository] — fully offline. The local DB is the source
 * of truth; rows are written `sync_status = PENDING` for the future sync engine.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on
 * sync). `owner_id` is persisted as the [Car] already carries it — never
 * fabricated client-side.
 */
internal class CarRepositoryImpl(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CarRepository {

    private val queries get() = database.carQueries

    override suspend fun add(car: Car): Either<DomainError, Car> = try {
        val now = clock.now().toString()
        database.transaction {
            // Honor the one-primary-per-owner invariant locally: demote any
            // existing primary before inserting a new primary car.
            if (car.isPrimary) {
                queries.clearPrimaryForOwner(updatedAt = now, ownerId = car.ownerId.value)
            }
            queries.insertCar(
                id = car.id.value,
                owner_id = car.ownerId.value,
                make = car.make,
                model = car.model,
                variant = car.variant,
                year = car.year.value.toLong(),
                fuel_type = car.fuelType.name,
                registration_number = car.registrationNumber?.value,
                current_odometer_km = car.odometer.km.toLong(),
                purchase_year = car.purchaseYear?.value?.toLong(),
                nickname = car.nickname,
                is_primary = if (car.isPrimary) 1L else 0L,
                created_at = now,
                updated_at = now,
                deleted_at = null,
                sync_status = SyncStatus.PENDING,
            )
        }
        car.right()
    } catch (e: Exception) {
        DomainError.PersistenceFailure(e.message).left()
    }

    override fun observePrimaryCar(): Flow<Car?> =
        queries.selectPrimaryCar()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
}
