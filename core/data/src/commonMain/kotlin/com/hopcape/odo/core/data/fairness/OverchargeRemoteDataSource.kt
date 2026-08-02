package com.hopcape.odo.core.data.fairness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where a filed overcharge report eventually goes. DB_SCHEMA has no `overcharge_reports`
 * table yet, so this port is the placeholder for a shape that is still to be agreed — which
 * is precisely why the repository writes locally first and treats the push as a later step.
 *
 * `:core:network` implements this once the server side exists.
 */
interface OverchargeRemoteDataSource {
    suspend fun push(reports: List<OverchargeReportDto>): List<OverchargeReportDto>
}

@Serializable
data class OverchargeReportDto(
    @SerialName("id") val id: String,
    @SerialName("service_log_id") val serviceLogId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("reason") val reason: String,
    @SerialName("service_category") val category: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Accepts everything, keeps nothing — the reports stay PENDING in the local table. */
internal class FakeOverchargeRemoteDataSource : OverchargeRemoteDataSource {
    override suspend fun push(reports: List<OverchargeReportDto>): List<OverchargeReportDto> = reports
}
