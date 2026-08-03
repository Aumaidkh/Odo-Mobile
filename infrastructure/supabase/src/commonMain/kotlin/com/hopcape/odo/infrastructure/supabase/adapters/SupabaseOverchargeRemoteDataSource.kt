package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.fairness.OverchargeReportDto
import com.hopcape.odo.core.data.fairness.OverchargeRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient

/**
 * Filed overcharge reports, upserted into `overcharge_reports`.
 *
 * > **The table does not exist yet.** DB_SCHEMA has no `overcharge_reports`, which is exactly
 * > why the repository writes locally first and treats the push as a later step. Against a live
 * > project this adapter will get a `PGRST205: table not found` and the reports stay PENDING,
 * > which is the correct outcome — nothing is lost, and the failure is loud. The table has to
 * > land in DB_SCHEMA before this does anything useful.
 */
internal class SupabaseOverchargeRemoteDataSource(
    private val postgrest: PostgrestClient,
) : OverchargeRemoteDataSource {

    override suspend fun push(reports: List<OverchargeReportDto>): List<OverchargeReportDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = OverchargeReportDto.serializer(),
            rows = reports,
        )

    private companion object {
        const val TABLE = "overcharge_reports"
    }
}
