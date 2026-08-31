package com.hopcape.odo.core.data.car

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * [CarRepository] over a [CarLocalDataSource] — fully offline; the local store is the
 * source of truth. This layer owns what an operation means: mapping storage failures to
 * [DomainError], telemetry, and asking the scheduler for a sync after a committed write.
 * How the rows are read and written lives behind [local].
 *
 * Not [Syncable][com.hopcape.odo.core.sync.Syncable] itself — the SQLDelight-backed
 * `SyncRunner` and `CarSyncTable` it would need live in `:infrastructure:database`, which
 * this module cannot depend on without a cycle. `CarSyncable`, in that module, wraps the
 * same runner this class used to hold.
 */
internal class CarRepositoryImpl(
    private val local: CarLocalDataSource,
    private val telemetry: DataTelemetry,
    private val scheduler: SyncScheduler,
) : CarRepository {

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
                local.insert(car)
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
                if (local.update(car)) {
                    requestSync(OP_UPDATE, car.id.value)
                    car.right()
                } else {
                    telemetry.failed(DataTelemetry.CAR, OP_UPDATE, DomainError.CarNotFound, car.id.value)
                    DomainError.CarNotFound.left()
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CAR, OP_UPDATE, e, car.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun softDelete(id: CarId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.CAR, OP_DELETE, id.value) {
            try {
                local.softDelete(id)
                requestSync(OP_DELETE, id.value)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.CAR, OP_DELETE, e, id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun findByRegistration(ownerId: OwnerId, registrationNumber: RegistrationNumber): Car? =
        local.findByRegistration(ownerId, registrationNumber)

    override fun observePrimaryCar(): Flow<Car?> =
        local.observePrimary().reportingFailures(OP_OBSERVE_PRIMARY, id = null)

    override fun observe(id: CarId): Flow<Car?> =
        local.observeById(id).reportingFailures(OP_OBSERVE_ONE, id.value)

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
