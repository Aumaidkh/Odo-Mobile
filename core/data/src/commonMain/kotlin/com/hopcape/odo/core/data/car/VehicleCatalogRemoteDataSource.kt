package com.hopcape.odo.core.data.car

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The server side of the shared make/model picker data: a public reference catalog anyone can
 * read, and an inbox for cars nobody has told Odo about yet.
 *
 * Unlike every other remote data source in this layer, [fetchMakes]/[fetchModels] are not
 * scoped to an owner — `vehicle_makes`/`vehicle_models` are shared, public tables everybody
 * reads the same rows from. That is also why this stays outside the `Syncable`/`Synchronizer`
 * engine (SYNC_DESIGN's push/pull is per-owner): this is a one-way, unscoped refresh, not a
 * delta sync.
 *
 * [push] is the other direction — an owner naming a car the catalog does not have. Submissions
 * land in a holding table, not straight into the catalog: an unreviewed typo or duplicate
 * spelling would otherwise be selectable by every other owner within a refresh.
 *
 * Unlike [fetchMakes]/[fetchModels], [push] *is* driven by the `Syncable`/`Synchronizer`
 * engine (`VehicleCatalogSubmissionSyncable`) — a submission is an owner-scoped write with its
 * own local outbox, not a one-way unscoped refresh, so it belongs in the ordinary sync pass
 * rather than behind a bespoke call site.
 */
interface VehicleCatalogRemoteDataSource {

    /** Every make in the shared catalog, in display order. */
    suspend fun fetchMakes(): List<VehicleMakeDto>

    /** Every model (and trim) in the shared catalog, in display order. */
    suspend fun fetchModels(): List<VehicleModelDto>

    /** Send unlisted-vehicle reports. The returned rows are what the server stored. */
    suspend fun push(submissions: List<VehicleCatalogSubmissionDto>): List<VehicleCatalogSubmissionDto>
}

/** The wire shape of one row of `vehicle_makes` — snake_case to match the Postgres columns. */
@Serializable
data class VehicleMakeDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("display_order") val displayOrder: Long,
)

/** The wire shape of one row of `vehicle_models` — snake_case to match the Postgres columns. */
@Serializable
data class VehicleModelDto(
    @SerialName("id") val id: String,
    @SerialName("make_id") val makeId: String,
    @SerialName("name") val name: String,
    @SerialName("variant") val variant: String? = null,
    @SerialName("display_order") val displayOrder: Long,
)

/**
 * One owner's report of a car the catalog is missing, bound for `vehicle_catalog_submissions`.
 *
 * [id] is client-generated, like every other synced entity's primary key — sent explicitly on
 * insert rather than left to the column's `default gen_random_uuid()`, which is what makes a
 * retried push idempotent (the same id upserts the same row instead of creating a twin).
 */
@Serializable
data class VehicleCatalogSubmissionDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("make") val make: String,
    @SerialName("model") val model: String,
    @SerialName("variant") val variant: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/**
 * Stand-in for builds with no Supabase configuration: the catalog fetch answers empty (the
 * local seeded table stays the only source), and a submission is accepted and forgotten.
 */
internal class FakeVehicleCatalogRemoteDataSource : VehicleCatalogRemoteDataSource {
    override suspend fun fetchMakes(): List<VehicleMakeDto> = emptyList()
    override suspend fun fetchModels(): List<VehicleModelDto> = emptyList()
    override suspend fun push(submissions: List<VehicleCatalogSubmissionDto>): List<VehicleCatalogSubmissionDto> = submissions
}
