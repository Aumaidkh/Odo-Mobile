package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.challan.ChallanDto
import com.hopcape.odo.core.data.challan.ChallanFetchDto
import com.hopcape.odo.core.data.challan.ChallanRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The challan records source over PostgREST — Odo's stand-in until a government API
 * exists, at which point *this class* is what gets replaced and nothing else.
 *
 * Two tables: `challan_vehicles` is the registry (which plates the records know at all —
 * what makes "No vehicle found" answerable), `challans` are the notices themselves.
 * Both are read-only reference data to the app except [markAllPendingPaid], which is the
 * one concession to this being Odo's own table: a real source would refuse it, and the
 * repository already treats the call as advisory.
 */
internal class SupabaseChallanRemoteDataSource(
    private val postgrest: PostgrestClient,
) : ChallanRemoteDataSource {

    override suspend fun fetch(regNo: String): ChallanFetchDto {
        val known = postgrest.select(
            table = TABLE_VEHICLES,
            serializer = VehicleRow.serializer(),
            filters = mapOf(COLUMN_REG_NO to "eq.$regNo"),
        )
        if (known.isEmpty()) return ChallanFetchDto(vehicleKnown = false, challans = emptyList())
        val challans = postgrest.select(
            table = TABLE_CHALLANS,
            serializer = ChallanDto.serializer(),
            filters = mapOf(COLUMN_REG_NO to "eq.$regNo"),
            order = "$COLUMN_ISSUED_ON.desc",
        )
        return ChallanFetchDto(vehicleKnown = true, challans = challans)
    }

    override suspend fun markAllPendingPaid(regNo: String) {
        postgrest.update(
            table = TABLE_CHALLANS,
            filters = mapOf(
                COLUMN_REG_NO to "eq.$regNo",
                COLUMN_STATUS to "eq.$STATUS_PENDING",
            ),
            patch = buildJsonObject { put(COLUMN_STATUS, JsonPrimitive(STATUS_PAID)) },
        )
    }

    /** The registry row — only the plate is ever read. */
    @Serializable
    private data class VehicleRow(@SerialName("reg_no") val regNo: String)

    private companion object {
        const val TABLE_VEHICLES = "challan_vehicles"
        const val TABLE_CHALLANS = "challans"
        const val COLUMN_REG_NO = "reg_no"
        const val COLUMN_STATUS = "status"
        const val COLUMN_ISSUED_ON = "issued_on"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PAID = "PAID"
    }
}
