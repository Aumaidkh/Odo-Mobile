package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.Syncable
import com.hopcape.odo.core.sync.Synchronizer
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

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
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
    private val remote: CarRemoteDataSource,
    private val syncTelemetry: SyncTelemetry,
    private val ownerId: () -> String?,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CarRepository, Syncable {

    private val queries get() = database.carQueries

    override val entity: SyncEntity = SyncEntity.CARS

    private val runner = SyncRunner(
        entity = SyncEntity.CARS,
        table = CarSyncTable(
            database = database,
            remote = remote,
            telemetry = syncTelemetry,
            ownerId = ownerId,
        ),
        database = database,
        telemetry = syncTelemetry,
        clock = clock,
    )

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = runner.run(synchronizer)

    /**
     * Tell the scheduler there is something worth pushing. Called after a write has
     * committed, never before, and only when it succeeded.
     *
     * Failure to *schedule* never fails the write: the data is safely local and `PENDING`,
     * and the next trigger will carry it.
     */
    private suspend fun requestSync(operation: String, id: String) {
        try {
            scheduler.requestSync(SyncReason.LocalWrite)
        } catch (e: Exception) {
            telemetry.crashed(DataTelemetry.CAR, "$operation.schedule", e, id)
        }
    }

    override suspend fun add(car: Car): Either<DomainError, Car> =
        telemetry.span(DataTelemetry.CAR, OP_ADD, car.id.value) {
            try {
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
                        // The reading arrived with the car, so it was written down now.
                        odometer_updated_at = now,
                        created_at = now,
                        updated_at = now,
                        deleted_at = null,
                        remote_version = null,
                        sync_status = SyncStatus.PENDING.name,
                    )
                }
                requestSync(OP_ADD, car.id.value)
                car.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CAR, OP_ADD, e, car.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun update(car: Car): Either<DomainError, Car> =
        telemetry.span(DataTelemetry.CAR, OP_UPDATE, car.id.value) {
            try {
                val now = clock.now().toString()
                // The existence check and the write share a transaction, so a car deleted
                // between them can't turn a CarNotFound into a silent no-op update.
                val result = database.transactionWithResult {
                    val stored = queries.selectById(car.id.value).executeAsOneOrNull()
                    if (stored == null) {
                        DomainError.CarNotFound.left()
                    } else {
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
                        car.right()
                    }
                }
                result
                    .onRight { requestSync(OP_UPDATE, car.id.value) }
                    .onLeft { telemetry.failed(DataTelemetry.CAR, OP_UPDATE, it, car.id.value) }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CAR, OP_UPDATE, e, car.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    /**
     * Tombstone the car together with its service logs and its documents.
     *
     * One transaction, because a car that is gone while its logs remain is a state no
     * screen can render: the logs are only reachable through the car, and a surviving
     * document would still count against the owner's free-tier allowance.
     *
     * The category rows under each entry are left alone — they belong to the entry, which
     * is still there as a tombstone that has to sync with its categories intact.
     */
    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.CAR, OP_DELETE, id.value) {
            try {
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
                    queries.softDeleteCar(deletedAt = now, syncStatus = pending, id = id.value)
                }
                requestSync(OP_DELETE, id.value)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CAR, OP_DELETE, e, id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override fun observePrimaryCar(): Flow<Car?> =
        queries.selectPrimaryCar()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
            .reportingFailures(OP_OBSERVE_PRIMARY, id = null)

    override fun observe(id: CarId): Flow<Car?> =
        queries.selectById(id.value)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
            .reportingFailures(OP_OBSERVE_ONE, id.value)

    /**
     * A read that throws would otherwise tear down the collecting screen. The stream stays
     * alive on `null` and the failure is reported, because a garage that quietly shows no
     * car looks exactly like an owner who has not set one up.
     */
    private fun Flow<Car?>.reportingFailures(operation: String, id: String?): Flow<Car?> =
        catch { cause ->
            telemetry.crashed(DataTelemetry.CAR, operation, cause, id)
            emit(null)
        }

    private companion object {
        const val OP_ADD = "add"
        const val OP_UPDATE = "update"
        const val OP_DELETE = "softDelete"
        const val OP_OBSERVE_PRIMARY = "observePrimary"
        const val OP_OBSERVE_ONE = "observeById"
    }
}
