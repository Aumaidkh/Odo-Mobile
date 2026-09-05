package com.hopcape.odo.core.domain.benchmark

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.VehicleSegment
import com.hopcape.odo.core.domain.shared.WorkshopTier

/**
 * One price somebody actually paid.
 *
 * **De-identified by design** — no owner, no car, no plate, no workshop name. The pool exists
 * so the next owner in the same city gets a band built from real bills instead of a
 * calculation, and none of that needs to know whose bill it was (DB_SCHEMA §13).
 */
data class PriceObservation(
    val categorySlug: String,
    val city: String,
    val amount: Amount,
    val segment: VehicleSegment?,
    val fuel: FuelType,
    val workshopTier: WorkshopTier,
    /** The make alone. It shapes a price and identifies nobody. */
    val carMake: String?,
)

/**
 * Gives a checked bill's prices back to the pool.
 *
 * This is what makes the "How we know" sheet's promise true — *"as real bills collect in this
 * bucket, it tightens on its own"*. Without it, a band computed from parts and labour stays
 * computed forever, however many owners check a bill.
 *
 * **Consent is the server's to enforce**, and it does: the insert policy requires the owner's
 * `shares_prices` to be set. This never asks, and never reports a refusal — a contribution is
 * a gift, not an action the owner is waiting on.
 */
fun interface FairnessContributor {

    suspend fun contribute(observations: List<PriceObservation>)
}
