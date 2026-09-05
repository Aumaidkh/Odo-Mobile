package com.hopcape.odo.core.data.schedule

/** The server side of the service schedule — the `service_schedule` reference table. */
interface ServiceIntervalRemoteDataSource {

    /** Every approved schedule row: the default set, plus every brand's exceptions. */
    suspend fun schedule(): List<ServiceIntervalDto>
}

/**
 * One schedule row.
 *
 * [brand] is null on a default rule, which is the row that applies to every make with no
 * exception of its own. It is the only place a brand name appears in the reference data.
 */
data class ServiceIntervalDto(
    val brand: String?,
    val slug: String,
    val displayName: String,
    val intervalKm: Int?,
    val intervalMonths: Int?,
)

/**
 * Knows no schedule.
 *
 * The honest stand-in for a build with no Supabase: every job's interval is unknown, and the
 * caller falls back to what it does without one rather than being handed an invented figure.
 */
internal class FakeServiceIntervalRemoteDataSource : ServiceIntervalRemoteDataSource {
    override suspend fun schedule(): List<ServiceIntervalDto> = emptyList()
}
