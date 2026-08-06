package com.hopcape.odo.core.data.car

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of the garage: push what changed locally, pull what changed remotely.
 *
 * Owner-scoped rather than car-scoped, unlike the service log and document ports — a car
 * *is* the scope those hang off, so there is nothing narrower to key on. The delta is
 * `owner_id = ? AND updated_at > cursor`.
 *
 * Cars are pulled before everything that references them (SYNC_DESIGN §8); a service log
 * arriving for a car this device has never seen would be a foreign-key error locally and on
 * the server both.
 */
interface CarRemoteDataSource {

    /** Cars changed since [since] (null = never synced, so everything). */
    suspend fun fetchSince(ownerId: String, since: Instant?): List<CarDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(cars: List<CarDto>): List<CarDto>

    /**
     * Clear `is_primary` on every live car of [ownerId] except [keepCarId].
     *
     * The server allows one primary car per owner. A fresh install onboards its car as
     * primary while the server may still hold another primary from before the reinstall, and
     * pushing the new one is then refused for good — this call makes room first. The demoted
     * rows get a new `updated_at` on the server, so other devices pull the change.
     */
    suspend fun demoteOtherPrimaries(ownerId: String, keepCarId: String)
}

/**
 * The wire shape of a car — snake_case to match the Postgres columns (DB_SCHEMA §9.3).
 *
 * [fuelType] carries the Postgres enum label, which is lowercase (`petrol`), while the local
 * column stores the Kotlin constant name (`PETROL`). The two are converted where rows become
 * DTOs, so this stays a faithful picture of the server row.
 *
 * [odometerUpdatedAt] is on the server but the client has so far treated it as local-only.
 * It is carried here so the day the mapper starts writing it, nothing else has to change —
 * an owner's odometer timeline currently resets on reinstall, and this is what fixes it.
 */
@Serializable
data class CarDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("make") val make: String,
    @SerialName("model") val model: String,
    @SerialName("variant") val variant: String? = null,
    @SerialName("year") val year: Int,
    @SerialName("fuel_type") val fuelType: String,
    @SerialName("registration_number") val registrationNumber: String? = null,
    @SerialName("current_odometer_km") val currentOdometerKm: Int,
    @SerialName("purchase_year") val purchaseYear: Int? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("odometer_updated_at") val odometerUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/**
 * Stand-in until a Supabase project is configured: accepts pushes, remembers nothing, and
 * has nothing to pull.
 *
 * Returns the pushed rows unchanged, which is honest about what it is — a server that agrees
 * with everything. It deliberately does **not** fabricate an `updated_at`: inventing one
 * would let local rows flip to SYNCED against a server that never saw them, and the first
 * real sync would then skip exactly the rows that were never sent.
 */
internal class FakeCarRemoteDataSource : CarRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<CarDto> = emptyList()
    override suspend fun push(cars: List<CarDto>): List<CarDto> = cars
    override suspend fun demoteOtherPrimaries(ownerId: String, keepCarId: String) = Unit
}
