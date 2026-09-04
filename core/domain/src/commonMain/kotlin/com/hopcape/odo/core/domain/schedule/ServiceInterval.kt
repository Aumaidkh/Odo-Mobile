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

    suspend fun intervals(): Either<DomainError, Map<String, ServiceInterval>>
}
