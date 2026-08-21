package com.hopcape.odo.infrastructure.database.health

import com.hopcape.odo.core.data.health.HealthScoreDto
import com.hopcape.odo.core.data.health.HealthScoreRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.Health_scores
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.orNullIfPlaceholder
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `health_scores` as the sync algorithm sees it.
 *
 * Append-only on both sides: a snapshot records what was true at a moment, so a row this
 * device already holds can never have changed. The conflict rules still run — they cost
 * nothing and a table that claims to be append-only is exactly the kind of claim that stops
 * being true — but in practice every pulled row is either new or identical.
 *
 * No enum conversion: every column is a number, a version string or a timestamp.
 */
internal class HealthScoreSyncTable(
    private val database: OdoDatabase,
    private val remote: HealthScoreRemoteDataSource,
    private val ownerId: () -> String?,
) : SyncTable<HealthScoreDto> {

    private val queries get() = database.healthScoreQueries

    override fun idOf(dto: HealthScoreDto): String = dto.id

    override fun updatedAtOf(dto: HealthScoreDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<HealthScoreDto> =
        queries.selectPending().executeAsList().map(Health_scores::toDto)

    override suspend fun push(rows: List<HealthScoreDto>): List<HealthScoreDto> = remote.push(rows)

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
    override suspend fun fetch(since: Instant?): FetchResult<HealthScoreDto> {
        val owner = ownerId().orNullIfPlaceholder() ?: return FetchResult.ScopeMissing(OWNER)
        return FetchResult.Rows(remote.fetchSince(owner, since))
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    override fun applyRemote(dto: HealthScoreDto) {
        queries.insertFromRemote(
            id = dto.id,
            car_id = dto.carId,
            owner_id = dto.ownerId,
            score = dto.score.toLong(),
            maintenance_pts = dto.maintenancePts.toLong(),
            documentation_pts = dto.documentationPts.toLong(),
            cost_efficiency_pts = dto.costEfficiencyPts.toLong(),
            history_pts = dto.historyPts.toLong(),
            algo_version = dto.algoVersion,
            computed_at = dto.computedAt,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
        )
        queries.updateFromRemote(
            car_id = dto.carId,
            owner_id = dto.ownerId,
            score = dto.score.toLong(),
            maintenance_pts = dto.maintenancePts.toLong(),
            documentation_pts = dto.documentationPts.toLong(),
            cost_efficiency_pts = dto.costEfficiencyPts.toLong(),
            history_pts = dto.historyPts.toLong(),
            algo_version = dto.algoVersion,
            computed_at = dto.computedAt,
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

private fun Health_scores.toDto() = HealthScoreDto(
    id = id,
    carId = car_id,
    ownerId = owner_id,
    score = score.toInt(),
    maintenancePts = maintenance_pts.toInt(),
    documentationPts = documentation_pts.toInt(),
    costEfficiencyPts = cost_efficiency_pts.toInt(),
    historyPts = history_pts.toInt(),
    algoVersion = algo_version,
    computedAt = computed_at,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
