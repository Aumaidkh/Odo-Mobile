package com.hopcape.odo.core.data.health

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of the score history.
 *
 * Append-only, like the local table: a snapshot records what was true at a moment, so rows
 * arrive and are never edited. That makes the pull unusually cheap — a row this device
 * already has can never have changed.
 *
 * Last in the sync order, and the only entity whose loss costs nothing permanent: every
 * score is recomputed on read, and this history exists only so "up 6 points this month" has
 * a number from a month ago to subtract.
 */
interface HealthScoreRemoteDataSource {

    /**
     * Everything on the account changed since [since] (null = never synced, so everything).
     *
     * Scoped to [ownerId] rather than to one car. A car id is not knowable at the moment a
     * pull runs — the cars themselves may only have arrived seconds earlier in the same run —
     * and scoping to one car also meant a second car's rows never arrived at all (issue
     * #312). `owner_id` is on every row and is what row-level security filters on anyway.
     */
    suspend fun fetchSince(ownerId: String, since: Instant?): List<HealthScoreDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(snapshots: List<HealthScoreDto>): List<HealthScoreDto>
}

/**
 * The wire shape of a score snapshot — snake_case to match the Postgres columns
 * (DB_SCHEMA §9.9).
 *
 * The server's `breakdown` jsonb is deliberately absent. The four point columns *are* the
 * breakdown here — a factor is a kind and its earned points, and the kinds are fixed by the
 * PRD — so there is nothing for it to carry that the row does not already say. Omitting it
 * leaves the server's default in place rather than overwriting it with null.
 */
@Serializable
data class HealthScoreDto(
    @SerialName("id") val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("score") val score: Int,
    @SerialName("maintenance_pts") val maintenancePts: Int,
    @SerialName("documentation_pts") val documentationPts: Int,
    @SerialName("cost_efficiency_pts") val costEfficiencyPts: Int,
    @SerialName("history_pts") val historyPts: Int,
    @SerialName("algo_version") val algoVersion: String,
    @SerialName("computed_at") val computedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** Accepts pushes, remembers nothing, has nothing to pull. */
internal class FakeHealthScoreRemoteDataSource : HealthScoreRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<HealthScoreDto> = emptyList()
    override suspend fun push(snapshots: List<HealthScoreDto>): List<HealthScoreDto> = snapshots
}
