package com.hopcape.odo.infrastructure.database.trip

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.data.trip.TripLocalDataSource
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.Trip
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQLDelight-backed [TripLocalDataSource]. Every write stamps `updated_at` and leaves the
 * row `sync_status = PENDING` — no pusher reads it yet (TRIPTRACKER_PLAN D3), but the
 * house rule applies from the first migration regardless.
 */
internal class SqlDelightTripLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TripLocalDataSource {

    private val queries get() = database.tripQueries
    private val parkedQueries get() = database.parkedLocationQueries

    override suspend fun insert(trip: Trip) {
        insertTripRow(trip, clock.now().toString())
    }

    override suspend fun insertWithParked(trip: Trip, gap: Trip?, parked: ParkedLocation) {
        val now = clock.now().toString()
        database.transaction {
            insertTripRow(trip, now)
            gap?.let { insertTripRow(it, now) }
            parkedQueries.upsert(
                carId = parked.carId.value,
                lat = parked.point.lat,
                lon = parked.point.lon,
                recordedAt = parked.recordedAt.toString(),
            )
        }
    }

    private fun insertTripRow(trip: Trip, now: String) {
        queries.insertTrip(
            id = trip.id.value,
            carId = trip.carId.value,
            ownerId = trip.ownerId.value,
            startedAt = trip.startedAt.toString(),
            endedAt = trip.endedAt.toString(),
            distanceM = trip.distance.meters,
            estimatedM = trip.estimatedDistance.meters,
            mode = trip.mode.name,
            status = trip.status.name,
            startLat = trip.startPoint?.lat,
            startLon = trip.startPoint?.lon,
            endLat = trip.endPoint?.lat,
            endLon = trip.endPoint?.lon,
            now = now,
            syncStatus = SyncStatus.PENDING.name,
        )
    }

    override suspend fun updateStatus(id: TripId, status: TripStatus): Boolean {
        val now = clock.now().toString()
        return database.transactionWithResult {
            if (queries.selectById(id.value, ::tripFromRow).executeAsOneOrNull() == null) {
                return@transactionWithResult false
            }
            queries.updateStatus(status = status.name, updatedAt = now, syncStatus = SyncStatus.PENDING.name, id = id.value)
            true
        }
    }

    override fun observeByCar(carId: CarId): Flow<List<Trip>> =
        queries.selectByCar(carId.value, ::tripFromRow)
            .asFlow()
            .mapToList(dispatcher)

    override fun observeNeedingConfirmation(carId: CarId): Flow<List<Trip>> =
        queries.selectNeedingConfirmation(carId.value, ::tripFromRow)
            .asFlow()
            .mapToList(dispatcher)

    override suspend fun countedSince(carId: CarId, after: Instant): List<Trip> =
        queries.selectCountedSince(carId.value, after.toString(), ::tripFromRow).executeAsList()

    override suspend fun parkedLocation(carId: CarId): ParkedLocation? =
        parkedQueries.selectFor(carId.value, ::parkedLocationFromRow).executeAsOneOrNull()
}
