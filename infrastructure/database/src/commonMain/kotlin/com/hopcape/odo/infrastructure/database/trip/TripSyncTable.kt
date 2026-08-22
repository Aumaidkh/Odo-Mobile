package com.hopcape.odo.infrastructure.database.trip

import com.hopcape.odo.core.data.trip.TripDto
import com.hopcape.odo.core.data.trip.TripRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Trips
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.orNullIfPlaceholder
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `trips` as the sync algorithm sees it (TRIPTRACKER_PLAN D3).
 *
 * Simpler than `ServiceLogSyncTable`, the pattern this mirrors: a trip has no blob to
 * upload and nothing to reconcile before a push, so [reconcileBeforePush] and
 * [uploadBlobs] are left at their no-op defaults.
 *
 * **The four coordinate columns never appear here.** [TripDto] has no fields for them
 * (TRIPTRACKER_PLAN D4), so there is nothing this class could push or apply even if it
 * tried — the exclusion is structural, not a filter this code has to remember to run.
 * `Trip.sq`'s `insertFromRemote`/`updateFromRemote` independently omit the same four
 * columns from their SET/column lists, so a pulled row cannot touch them either.
 */
internal class TripSyncTable(
    private val database: OdoDatabase,
    private val remote: TripRemoteDataSource,
    private val ownerId: () -> String?,
) : SyncTable<TripDto> {

    private val queries get() = database.tripQueries

    override fun idOf(dto: TripDto): String = dto.id

    override fun updatedAtOf(dto: TripDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<TripDto> =
        queries.selectPending().executeAsList().map(Trips::toDto)

    override suspend fun push(rows: List<TripDto>): List<TripDto> = remote.push(rows)

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
    override suspend fun fetch(since: Instant?): FetchResult<TripDto> {
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

    /**
     * Insert-then-update, the house two-step for a driver with no `UPSERT` (SQLite 3.18).
     * Neither query below carries the coordinate columns — `Trip.sq` never gives them one
     * to write.
     */
    override fun applyRemote(dto: TripDto) {
        queries.insertFromRemote(
            id = dto.id,
            car_id = dto.carId,
            owner_id = dto.ownerId,
            started_at = dto.startedAt,
            ended_at = dto.endedAt,
            distance_m = dto.distanceM,
            estimated_m = dto.estimatedM,
            mode = dto.mode.uppercase(),
            status = dto.status.uppercase(),
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateFromRemote(
            car_id = dto.carId,
            owner_id = dto.ownerId,
            started_at = dto.startedAt,
            ended_at = dto.endedAt,
            distance_m = dto.distanceM,
            estimated_m = dto.estimatedM,
            mode = dto.mode.uppercase(),
            status = dto.status.uppercase(),
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

/** DB row → wire shape. `mode`/`status` are Kotlin enum constant names locally, lowercase on the wire. */
private fun Trips.toDto() = TripDto(
    id = id,
    carId = car_id,
    ownerId = owner_id,
    startedAt = started_at,
    endedAt = ended_at,
    distanceM = distance_m,
    estimatedM = estimated_m,
    mode = mode.lowercase(),
    status = status.lowercase(),
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
