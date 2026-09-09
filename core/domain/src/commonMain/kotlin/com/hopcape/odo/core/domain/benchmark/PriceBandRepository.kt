package com.hopcape.odo.core.domain.benchmark

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.VehicleSegment
import com.hopcape.odo.core.domain.shared.WorkshopTier

/**
 * What a job normally costs.
 *
 * Every price claim the app makes comes through here, so the two ways of having no answer
 * stay apart: a `Left` is "we could not ask", and a `null` is "we asked and there is nothing
 * for this job". The first deserves a retry, the second deserves a line saying so — and
 * showing either as an overcharge would be the worst thing this feature could do.
 */
fun interface PriceBandRepository {

    suspend fun bandFor(query: PriceBandQuery): Either<DomainError, PriceBand?>
}

/**
 * One question for the band table.
 *
 * Everything but the job and the city is optional, and the server widens its search when a
 * field is missing rather than refusing. That is the point of the ladder: a car whose segment
 * Odo could not work out still gets a city-wide answer instead of nothing.
 */
data class PriceBandQuery(
    /** The server's category slug, e.g. `ac_service`. Not a display name. */
    val categorySlug: String,
    val city: String,
    val segment: VehicleSegment? = null,
    val fuel: FuelType? = null,
    val workshopTier: WorkshopTier? = null,
)
