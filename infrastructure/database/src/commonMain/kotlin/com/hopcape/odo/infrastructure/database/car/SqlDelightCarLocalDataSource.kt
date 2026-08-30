package com.hopcape.odo.infrastructure.database.car

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.data.car.CarLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [CarLocalDataSource] — fully offline. The local DB is the source
 * of truth; every write stamps `updated_at` and leaves the row `sync_status = PENDING`
 * for the sync engine.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on sync).
 * `owner_id` is persisted as the [Car] already carries it — never fabricated here.
 */
internal class SqlDelightCarLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CarLocalDataSource {

    private val queries get() = database.carQueries

    override suspend fun insert(car: Car) {
        val now = clock.now().toString()
        database.transaction {
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
                // The reading arrived with the car, so it was written down now.
                odometer_updated_at = now,
                created_at = now,
                updated_at = now,
                deleted_at = null,
                remote_version = null,
                sync_status = SyncStatus.PENDING.name,
            )
        }
    }

    override suspend fun update(car: Car): Boolean {
        val now = clock.now().toString()
        return database.transactionWithResult {
            val stored = queries.selectById(car.id.value).executeAsOneOrNull()
                ?: return@transactionWithResult false
            if (car.isPrimary) {
                queries.clearPrimaryForOwner(updatedAt = now, ownerId = car.ownerId.value)
            }
            // Only a changed reading was written down now. Re-dating it on a
            // nickname edit would claim the odometer was checked when it wasn't.
            val odometerUpdatedAt =
                if (stored.current_odometer_km == car.odometer.km.toLong()) {
                    stored.odometer_updated_at
                } else {
                    now
                }
            queries.updateCar(
                make = car.make,
                model = car.model,
                variant = car.variant,
                year = car.year.value.toLong(),
                fuelType = car.fuelType.name,
                registrationNumber = car.registrationNumber?.value,
                odometerKm = car.odometer.km.toLong(),
                purchaseYear = car.purchaseYear?.value?.toLong(),
                nickname = car.nickname,
                isPrimary = if (car.isPrimary) 1L else 0L,
                odometerUpdatedAt = odometerUpdatedAt,
                updatedAt = now,
                // An edited row has to reach the server again.
                syncStatus = SyncStatus.PENDING.name,
                id = car.id.value,
            )
            true
        }
    }

    /**
     * One transaction, because a car that is gone while its logs remain is a state no
     * screen can render: the logs are only reachable through the car, and a surviving
     * document would still count against the owner's free-tier allowance.
     *
     * The category rows under each entry are left alone — they belong to the entry, which
     * is still there as a tombstone that has to sync with its categories intact.
     */
    override suspend fun softDelete(id: CarId) {
        val now = clock.now().toString()
        val pending = SyncStatus.PENDING.name
        database.transaction {
            database.serviceLogQueries.softDeleteServiceLogsForCar(
                deletedAt = now,
                syncStatus = pending,
                carId = id.value,
            )
            database.documentQueries.softDeleteDocumentsForCar(
                deletedAt = now,
                syncStatus = pending,
                carId = id.value,
            )
            database.fuelFillQueries.softDeleteFuelFillsForCar(
                deletedAt = now,
                syncStatus = pending,
                carId = id.value,
            )
            // A custom reminder of a deleted car must never fire again, and its
            // dismissals go with it.
            database.reminderQueries.softDeleteRemindersForCar(
                deletedAt = now,
                syncStatus = pending,
                carId = id.value,
            )
            queries.softDeleteCar(deletedAt = now, syncStatus = pending, id = id.value)
        }
    }

    override suspend fun findByRegistration(ownerId: OwnerId, registrationNumber: RegistrationNumber): Car? =
        queries.selectByOwnerAndRegistration(
            ownerId = ownerId.value,
            registrationNumber = registrationNumber.value,
        ).executeAsOneOrNull()?.toDomain()

    override fun observePrimary(): Flow<Car?> =
        queries.selectPrimaryCar()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }

    override fun observeById(id: CarId): Flow<Car?> =
        queries.selectById(id.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
}
