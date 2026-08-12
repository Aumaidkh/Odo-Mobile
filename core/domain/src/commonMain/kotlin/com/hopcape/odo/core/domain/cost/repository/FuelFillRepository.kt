package com.hopcape.odo.core.domain.cost.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Port for persisting a car's fuel fills. The implementation lives in `:core:data`; the domain
 * stays ignorant of it.
 *
 * One method, because one thing writes fills and nothing reads them yet. The reads arrive with
 * the cost tracker that shows them, together with the mileage calculation they feed — writing
 * them now would be guessing at the shape of a query nobody has asked for.
 *
 * No update either. A fill records a payment that already happened at a pump; correcting one
 * means deleting it and recording what actually occurred, and an editable payment record is one
 * nobody can rely on.
 */
fun interface FuelFillRepository {

    suspend fun add(fill: FuelFill): Either<DomainError, FuelFill>
}
