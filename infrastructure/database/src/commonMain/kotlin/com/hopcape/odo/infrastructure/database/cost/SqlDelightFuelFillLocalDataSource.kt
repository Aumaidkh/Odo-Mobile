package com.hopcape.odo.infrastructure.database.cost

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hopcape.odo.core.data.cost.FuelFillLocalDataSource
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [FuelFillLocalDataSource] — fully offline. The local DB is the source
 * of truth; every write stamps `updated_at` and leaves the row `sync_status = PENDING`.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on sync, once
 * a `fuel_fills` table exists to sync against).
 */
internal class SqlDelightFuelFillLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FuelFillLocalDataSource {

    private val queries get() = database.fuelFillQueries

    override suspend fun insert(fill: FuelFill) {
        val now = clock.now().toString()
        queries.insertFuelFill(
            id = fill.id.value,
            carId = fill.carId.value,
            ownerId = fill.ownerId.value,
            filledOn = fill.filledOn.toString(),
            odometerKm = fill.odometer?.km?.toLong(),
            quantityMilli = fill.quantityMilli,
            fuelUnit = fill.unit.name,
            amountPaise = fill.amount.paise,
            stationName = fill.stationName,
            transactionRef = fill.transactionRef,
            entrySource = fill.entrySource.name,
            now = now,
            syncStatus = SyncStatus.PENDING.name,
        )
    }

    override fun observeByCar(carId: CarId): Flow<List<FuelFill>> =
        queries.selectFillsByCar(carId.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun latestForCar(carId: CarId): FuelFill? =
        queries.selectLatestFillForCar(carId.value).executeAsOneOrNull()?.toDomain()

    override suspend fun countBySource(carId: CarId, source: FillEntrySource): Int =
        queries.countFillsBySource(carId = carId.value, entrySource = source.name)
            .executeAsOne()
            .toInt()
}
