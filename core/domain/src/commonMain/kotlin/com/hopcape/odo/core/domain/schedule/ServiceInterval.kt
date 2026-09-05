package com.hopcape.odo.core.domain.schedule

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * How often the maker says a job comes round.
 *
 * Both figures are optional and often only one is set: an air filter is a distance, a brake
 * fluid change is a time, and plenty of jobs are neither. A missing interval is not a fault —
 * it means the schedule has nothing to say, and the caller has to decide what to do without
 * it rather than assume a number.
 */
data class ServiceInterval(
    /** The server's category slug, e.g. `engine_oil`. */
    val slug: String,
    /** What a screen calls it — "Engine oil + filter", not the slug. */
    val displayName: String,
    val km: Int? = null,
    val months: Int? = null,
) {
    val isKnown: Boolean get() = km != null || months != null
}

/**
 * The maker's service schedule, as the app reads it.
 *
 * The whole table at once rather than one job at a time: it is two dozen static rows that
 * every line of a bill is checked against, and a call per line would be two dozen round trips
 * to answer one screen.
 */
fun interface ServiceIntervalRepository {

    /**
     * The schedule for one make, keyed by slug.
     *
     * [brand] picks the exception rows: a make with a rule of its own overrides the default
     * for that job, and every other job falls back to the default. Null, or a make the table
     * has no exception for, gets the default set — which is most cars, and is the answer a
     * five-minute-old install has to work from.
     */
    suspend fun intervals(brand: String?): Either<DomainError, Map<String, ServiceInterval>>
}
