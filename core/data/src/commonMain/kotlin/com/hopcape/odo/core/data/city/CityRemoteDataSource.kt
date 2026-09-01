package com.hopcape.odo.core.data.city

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of the shared city lookup — a public reference table anyone can read.
 *
 * Unlike every other remote data source in this layer, [fetchSince] is not scoped to an owner:
 * `cities` is a shared, public table everybody reads the same rows from. Unlike
 * [com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource]'s makes/models fetch, this
 * one *is* driven by the `Syncable`/`Synchronizer` engine (`CitySyncable`) — [since] is an
 * ordinary delta cursor, just with no owner to scope it by, which is what makes a pull-only
 * `Syncable` for shared reference data a natural fit rather than a bespoke refresher.
 */
interface CityRemoteDataSource {

    /** Every city changed since [since] (null = never synced). */
    suspend fun fetchSince(since: Instant?): List<CityDto>
}

/** The wire shape of one row of `cities` — snake_case to match the Postgres columns. */
@Serializable
data class CityDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("state") val state: String,
    @SerialName("tier") val tier: Int,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("updated_at") val updatedAt: String,
)

/** Stand-in for builds with no Supabase configuration: the picker offers nothing. */
internal class FakeCityRemoteDataSource : CityRemoteDataSource {
    override suspend fun fetchSince(since: Instant?): List<CityDto> = emptyList()
}
