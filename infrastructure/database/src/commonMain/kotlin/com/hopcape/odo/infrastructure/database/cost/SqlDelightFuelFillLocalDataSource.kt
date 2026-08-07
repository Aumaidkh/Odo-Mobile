package com.hopcape.odo.infrastructure.database.cost

import com.hopcape.odo.core.data.cost.FuelFillLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.cost.model.FuelFill
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
) : FuelFillLocalDataSource {

    private val queries get() = database.fuelFillQueries

    override suspend fun insert(fill: FuelFill) {
        val now = clock.now().toString()
        queries.insertFuelFill(
            id = fill.id.value,
            carId = fill.carId.value,
            ownerId = fill.ownerId.value,
            filledOn = fill.filledOn.toString(),
            odometerKm = fill.odometer.km.toLong(),
            quantityMilli = fill.quantityMilli,
            fuelUnit = fill.unit.name,
            amountPaise = fill.amount.paise,
            stationName = fill.stationName,
            paidVia = fill.paidVia.name,
            transactionRef = fill.transactionRef,
            now = now,
            syncStatus = SyncStatus.PENDING.name,
        )
    }
}
