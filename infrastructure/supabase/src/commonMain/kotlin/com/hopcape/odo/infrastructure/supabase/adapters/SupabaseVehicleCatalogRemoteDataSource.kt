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
 * in this file. [submitUnlisted] is the only write, and it is a plain insert: the submission
 * carries no id the client ever needs again, so there is nothing to upsert against.
 */
internal class SupabaseVehicleCatalogRemoteDataSource(
    private val postgrest: PostgrestClient,
) : VehicleCatalogRemoteDataSource {

    override suspend fun fetchMakes(): List<VehicleMakeDto> =
        postgrest.select(table = MAKES_TABLE, serializer = VehicleMakeDto.serializer(), order = "$COLUMN_DISPLAY_ORDER.asc")

    override suspend fun fetchModels(): List<VehicleModelDto> =
        postgrest.select(table = MODELS_TABLE, serializer = VehicleModelDto.serializer(), order = "$COLUMN_DISPLAY_ORDER.asc")

    override suspend fun submitUnlisted(submission: VehicleCatalogSubmissionDto) {
        postgrest.upsert(
            table = SUBMISSIONS_TABLE,
            serializer = VehicleCatalogSubmissionDto.serializer(),
            rows = listOf(submission),
            returnRows = false,
        )
    }

    private companion object {
        const val MAKES_TABLE = "vehicle_makes"
        const val MODELS_TABLE = "vehicle_models"
        const val SUBMISSIONS_TABLE = "vehicle_catalog_submissions"
        const val COLUMN_DISPLAY_ORDER = "display_order"
    }
}
