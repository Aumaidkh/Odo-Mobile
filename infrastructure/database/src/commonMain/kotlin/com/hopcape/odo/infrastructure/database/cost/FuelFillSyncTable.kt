package com.hopcape.odo.infrastructure.database.cost

import com.hopcape.odo.core.data.cost.FuelFillDto
import com.hopcape.odo.core.data.cost.FuelFillRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.Fuel_fills
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.orNullIfPlaceholder
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `fuel_fills` as the sync algorithm sees it.
 *
 * Rows only — a fill has no blob to upload and nothing to reconcile before a push, so
 * [uploadBlobs] and `reconcileBeforePush` stay at their no-op defaults. This mirrors
 * [com.hopcape.odo.infrastructure.database.trip.TripSyncTable] rather than the document one
 * for that reason.
 *
 * **Only confirmed fills are here, and that is structural rather than a filter.** A detection
 * the owner has not answered lives in `pending_fills`, a different table with no sync columns
 * at all, and nothing moves a row between the two — the confirm step writes a fresh
 * `fuel_fills` row and marks the pending one resolved. So there is no path by which Odo's
 * guess about somebody's payment could reach a server.
 *
 * `odometer_km` is nullable on both sides. It is the one column that can be absent on a real
 * row, because the confirm step no longer demands a reading, and a pull must be able to carry
 * that absence rather than substitute a zero.
 */
internal class FuelFillSyncTable(
    private val database: OdoDatabase,
    private val remote: FuelFillRemoteDataSource,
    private val ownerId: () -> String?,
) : SyncTable<FuelFillDto> {

    private val queries get() = database.fuelFillQueries

    override fun idOf(dto: FuelFillDto): String = dto.id

    override fun updatedAtOf(dto: FuelFillDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<FuelFillDto> =
        queries.selectPending().executeAsList().map(Fuel_fills::toDto)

    override suspend fun push(rows: List<FuelFillDto>): List<FuelFillDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    /**
     * Scoped to the **owner**, not to one car.
     *
     * It used to read the active car off `ActiveCarProvider.activeCarId`, a StateFlow seeded
     * null and fed by a database query. On the first run after signing in, the engine wrote
     * the pulled cars and reached this table milliseconds later — before that flow had
     * re-emitted — so the fetch returned nothing, the pull reported success, and WorkManager
     * dropped the job with none of the owner's history fetched (issue #312). It also meant a
     * second car's rows never arrived, and that an account whose server rows all carry
     * `is_primary = false` never pulled here at all.
     *
     * The owner id comes from the session synchronously, so there is no flow to lose a race
     * with, and `owner_id` is the column row-level security already filters on server-side.
     */
    override suspend fun fetch(since: Instant?): FetchResult<FuelFillDto> {
        val owner = ownerId().orNullIfPlaceholder() ?: return FetchResult.ScopeMissing(OWNER)
        return FetchResult.Rows(remote.fetchSince(owner, since))
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(
                syncStatus = row.sync_status.toSyncStatus(),
                updatedAt = row.updated_at.toInstantOrNull(),
            )
        }

    override fun applyRemote(dto: FuelFillDto) {
        queries.insertFromRemote(
            id = dto.id,
            car_id = dto.carId,
            owner_id = dto.ownerId,
            filled_on = dto.filledOn,
            odometer_km = dto.odometerKm,
            quantity_milli = dto.quantityMilli,
            fuel_unit = dto.fuelUnit,
            amount_paise = dto.amountPaise,
            station_name = dto.stationName,
            transaction_ref = dto.transactionRef,
            entry_source = dto.entrySource,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateFromRemote(
            car_id = dto.carId,
            owner_id = dto.ownerId,
            filled_on = dto.filledOn,
            odometer_km = dto.odometerKm,
            quantity_milli = dto.quantityMilli,
            fuel_unit = dto.fuelUnit,
            amount_paise = dto.amountPaise,
            station_name = dto.stationName,
            transaction_ref = dto.transactionRef,
            entry_source = dto.entrySource,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )
    }

    private companion object {
        /** Names the missing scope in a log. Never the value. */
        const val OWNER = "owner id"
    }
}

private fun Fuel_fills.toDto() = FuelFillDto(
    id = id,
    carId = car_id,
    ownerId = owner_id,
    filledOn = filled_on,
    odometerKm = odometer_km,
    quantityMilli = quantity_milli,
    fuelUnit = fuel_unit,
    amountPaise = amount_paise,
    stationName = station_name,
    transactionRef = transaction_ref,
    entrySource = entry_source,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
