package com.hopcape.odo.core.data.schedule

/** The server side of the service schedule — the `service_categories` reference table. */
interface ServiceIntervalRemoteDataSource {

    /** Every active category and what the maker's schedule says about it. */
    suspend fun intervals(): List<ServiceIntervalDto>
}

data class ServiceIntervalDto(
    val slug: String,
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
    override suspend fun intervals(): List<ServiceIntervalDto> = emptyList()
}
