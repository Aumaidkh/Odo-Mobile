package com.hopcape.odo.core.data.trip

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.core.domain.trip.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlin.time.Instant

/**
 * [TripRepository] over a [TripLocalDataSource] — offline-first; the local store is the
 * source of truth. No [com.hopcape.odo.core.sync.SyncScheduler]: there is no
 * `SyncEntity.TRIPS`/`Syncable` yet (TRIPTRACKER_PLAN D3), so there is nothing to ask for
 * a sync — rows sit `PENDING` until a later slice adds one.
 */
internal class TripRepositoryImpl(
    private val local: TripLocalDataSource,
    private val telemetry: DataTelemetry,
) : TripRepository {

    override suspend fun add(trip: Trip): Either<DomainError, Trip> =
        telemetry.span(DataTelemetry.TRIP, OP_ADD, trip.id.value) {
            try {
                local.insert(trip)
                trip.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.TRIP, OP_ADD, e, trip.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun addWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation): Either<DomainError, Trip> =
        telemetry.span(DataTelemetry.TRIP, OP_ADD_WITH_PARKED, trip.id.value) {
            try {
                local.insertWithParked(trip, gap, parked)
                trip.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.TRIP, OP_ADD_WITH_PARKED, e, trip.id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override fun observe(carId: CarId): Flow<List<Trip>> =
        local.observeByCar(carId).reportingFailures(OP_OBSERVE_CAR, carId.value)

    override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> =
        local.observeNeedingConfirmation(carId).reportingFailures(OP_OBSERVE_NEEDS_CONFIRMATION, carId.value)

    override suspend fun setStatus(id: TripId, status: TripStatus): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.TRIP, OP_SET_STATUS, id.value) {
            try {
                if (local.updateStatus(id, status)) {
                    Unit.right()
                } else {
                    telemetry.failed(DataTelemetry.TRIP, OP_SET_STATUS, DomainError.TripNotFound, id.value)
                    DomainError.TripNotFound.left()
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.TRIP, OP_SET_STATUS, e, id.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> =
        telemetry.span(DataTelemetry.TRIP, OP_COUNTED_SINCE, carId.value) {
            try {
                local.countedSince(carId, after)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.TRIP, OP_COUNTED_SINCE, e, carId.value)
                emptyList()
            }
        }

    override suspend fun countedBetween(carId: CarId, from: Instant, to: Instant): List<Trip> =
        telemetry.span(DataTelemetry.TRIP, OP_COUNTED_BETWEEN, carId.value) {
            try {
                local.countedBetween(carId, from, to)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.TRIP, OP_COUNTED_BETWEEN, e, carId.value)
                emptyList()
            }
        }

    override suspend fun parkedLocation(carId: CarId): ParkedLocation? =
        telemetry.span(DataTelemetry.TRIP, OP_PARKED_LOCATION, carId.value) {
            try {
                local.parkedLocation(carId)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.TRIP, OP_PARKED_LOCATION, e, carId.value)
                null
            }
        }

    override suspend fun deleteAllForCar(carId: CarId): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.TRIP, OP_DELETE_ALL_FOR_CAR, carId.value) {
            try {
                local.deleteAllForCar(carId)
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.TRIP, OP_DELETE_ALL_FOR_CAR, e, carId.value)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    override suspend fun forgetRoutes(): Either<DomainError, Unit> =
        telemetry.span(DataTelemetry.TRIP, OP_FORGET_ROUTES) {
            try {
                local.forgetRoutes()
                Unit.right()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.TRIP, OP_FORGET_ROUTES, e)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    private fun Flow<List<Trip>>.reportingFailures(operation: String, id: String): Flow<List<Trip>> =
        catch { cause ->
            telemetry.crashed(DataTelemetry.TRIP, operation, cause, id)
            emit(emptyList())
        }

    private companion object {
        const val OP_ADD = "add"
        const val OP_ADD_WITH_PARKED = "addWithParked"
        const val OP_OBSERVE_CAR = "observe"
        const val OP_OBSERVE_NEEDS_CONFIRMATION = "observeNeedingConfirmation"
        const val OP_SET_STATUS = "setStatus"
        const val OP_COUNTED_SINCE = "countedSince"
        const val OP_COUNTED_BETWEEN = "countedBetween"
        const val OP_PARKED_LOCATION = "parkedLocation"
        const val OP_DELETE_ALL_FOR_CAR = "deleteAllForCar"
        const val OP_FORGET_ROUTES = "forgetRoutes"
    }
}
