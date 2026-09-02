package com.hopcape.odo.core.data.city

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The server side of the city inbox — an owner naming a city the catalog does not have.
 *
 * Submissions land in a holding table, not straight into the catalog: an unreviewed typo or
 * duplicate spelling would otherwise be selectable by every other owner within a refresh. Push
 * only — city_submissions has no owner-facing lifecycle this device ever reads back, the same
 * shape as [com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource]'s submission half.
 */
interface CitySubmissionRemoteDataSource {

    /** Send unlisted-city reports. The returned rows are what the server stored. */
    suspend fun push(submissions: List<CitySubmissionDto>): List<CitySubmissionDto>
}

/**
 * One owner's report of a city the catalog is missing, bound for `city_submissions`.
 *
 * [id] is client-generated, like every other synced entity's primary key — sent explicitly on
 * insert rather than left to the column's `default gen_random_uuid()`, which is what makes a
 * retried push idempotent (the same id upserts the same row instead of creating a twin).
 */
@Serializable
data class CitySubmissionDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("name") val name: String,
    @SerialName("created_at") val createdAt: String,
)

/** Stand-in for builds with no Supabase configuration: a submission is accepted and forgotten. */
internal class FakeCitySubmissionRemoteDataSource : CitySubmissionRemoteDataSource {
    override suspend fun push(submissions: List<CitySubmissionDto>): List<CitySubmissionDto> = submissions
}
