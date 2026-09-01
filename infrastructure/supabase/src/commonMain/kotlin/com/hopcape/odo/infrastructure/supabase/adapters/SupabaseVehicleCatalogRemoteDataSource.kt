package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.car.VehicleCatalogSubmissionDto
import com.hopcape.odo.core.data.car.VehicleMakeDto
import com.hopcape.odo.core.data.car.VehicleModelDto
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient

/**
 * `vehicle_makes` / `vehicle_models` / `vehicle_catalog_submissions` over PostgREST.
 *
 * The two fetches are plain, unscoped selects — these tables carry no `owner_id` and no RLS
 * beyond "anyone may read" (SUPABASE_BOOTSTRAP §vehicle catalog), unlike every other adapter
 * in this file. [push] is the only write, and deliberately a plain [PostgrestClient.insert]
 * with `returnRows = false`, never [PostgrestClient.upsert] and never `return=representation`:
 * `vehicle_catalog_submissions` grants `authenticated` INSERT only (SUPABASE_BOOTSTRAP §vehicle
 * catalog submissions) — no UPDATE, no SELECT, by design, since a submission is reviewed and
 * promoted by hand from the SQL editor, never read back or changed by the app.
 *
 * **Both alternatives fail RLS, not just the obvious one.** An upsert's `ON CONFLICT DO
 * UPDATE` plan needs the UPDATE policy this table intentionally does not have. Less obviously,
 * `INSERT ... RETURNING` needs the SELECT policy it also intentionally does not have — Postgres
 * checks the just-inserted row against the SELECT policies before handing it back, RLS or not,
 * and a table with zero SELECT policies denies that by default. Verified directly against the
 * live database: the identical INSERT succeeds with `returnRows = false` and fails with
 * `42501` the moment `RETURNING` is added, regardless of upsert vs. plain insert.
 *
 * That means the accepted rows can never be read back to confirm what the server stored, so
 * [push] answers with [submissions] itself rather than a server-echoed list — a call that
 * didn't throw is the only signal this table's RLS will ever give.
 */
internal class SupabaseVehicleCatalogRemoteDataSource(
    private val postgrest: PostgrestClient,
) : VehicleCatalogRemoteDataSource {

    override suspend fun fetchMakes(): List<VehicleMakeDto> =
        postgrest.select(table = MAKES_TABLE, serializer = VehicleMakeDto.serializer(), order = "$COLUMN_DISPLAY_ORDER.asc")

    override suspend fun fetchModels(): List<VehicleModelDto> =
        postgrest.select(table = MODELS_TABLE, serializer = VehicleModelDto.serializer(), order = "$COLUMN_DISPLAY_ORDER.asc")

    override suspend fun push(submissions: List<VehicleCatalogSubmissionDto>): List<VehicleCatalogSubmissionDto> {
        postgrest.insert(
            table = SUBMISSIONS_TABLE,
            serializer = VehicleCatalogSubmissionDto.serializer(),
            rows = submissions,
            returnRows = false,
        )
        return submissions
    }

    private companion object {
        const val MAKES_TABLE = "vehicle_makes"
        const val MODELS_TABLE = "vehicle_models"
        const val SUBMISSIONS_TABLE = "vehicle_catalog_submissions"
        const val COLUMN_DISPLAY_ORDER = "display_order"
    }
}
