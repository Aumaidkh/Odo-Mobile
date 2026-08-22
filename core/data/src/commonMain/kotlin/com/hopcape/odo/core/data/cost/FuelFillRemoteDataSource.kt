package com.hopcape.odo.core.data.cost

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of fuel fills: push what was confirmed locally, pull what changed remotely.
 *
 * A port, declared here in `:core:data` because this is the layer that knows how a row
 * becomes a payload; `:infrastructure:supabase` implements it.
 *
 * Only fills the owner confirmed ever reach this. A detection still waiting for an answer
 * lives in `pending_fills`, which is device-local — it is a question this phone has not asked
 * yet, and pushing it would put Odo's guess about someone's payment on a server.
 */
interface FuelFillRemoteDataSource {

    /**
     * Everything on the account changed since [since] (null = never synced, so everything).
     *
     * Scoped to [ownerId] rather than to one car. A car id is not knowable at the moment a
     * pull runs — the cars themselves may only have arrived seconds earlier in the same run —
     * and scoping to one car also meant a second car's rows never arrived at all (issue
     * #312). `owner_id` is on every row and is what row-level security filters on anyway.
     */
    suspend fun fetchSince(ownerId: String, since: Instant?): List<FuelFillDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(fills: List<FuelFillDto>): List<FuelFillDto>
}

/**
 * The wire shape of a fill — snake_case to match the Postgres columns, so the same DTO serves
 * the PostgREST payload without a second mapping.
 *
 * Every nullable field is declared with an explicit default and is always serialised, never
 * omitted. PostgREST rejects a batch whose objects do not share a key set (PGRST102), and a
 * field left out of an update is a field that never clears on the server — a station name the
 * owner deleted would come straight back on the next pull.
 *
 * [odometerKm] is nullable because the reading is optional on a fill: a detected fill reaches
 * the owner while they are still at the pump, where the dashboard is out of reach.
 *
 * [entrySource] carries the capture channel as its Kotlin constant name (`DETECTED`,
 * `PUMP_OCR`, `PREFILLED`, `MANUAL`), matching the local column — the server column is plain
 * `text` with a CHECK rather than a Postgres enum, so the two need no case conversion.
 */
@Serializable
data class FuelFillDto(
    @SerialName("id") val id: String,
    @SerialName("car_id") val carId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("filled_on") val filledOn: String,
    @SerialName("odometer_km") val odometerKm: Long? = null,
    @SerialName("quantity_milli") val quantityMilli: Long,
    @SerialName("fuel_unit") val fuelUnit: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("station_name") val stationName: String? = null,
    @SerialName("transaction_ref") val transactionRef: String? = null,
    @SerialName("entry_source") val entrySource: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/**
 * Stand-in for builds with no Supabase configuration: accepts pushes, remembers nothing, and
 * has nothing to pull.
 *
 * It returns the pushed rows back unchanged, which is honest about what it is — a server that
 * agrees with everything. It deliberately does **not** fabricate a `remote_version`: inventing
 * one would let local rows flip to SYNCED against a server that never saw them, and the first
 * real sync would then skip exactly the rows that were never sent.
 */
internal class FakeFuelFillRemoteDataSource : FuelFillRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<FuelFillDto> = emptyList()
    override suspend fun push(fills: List<FuelFillDto>): List<FuelFillDto> = fills
}
