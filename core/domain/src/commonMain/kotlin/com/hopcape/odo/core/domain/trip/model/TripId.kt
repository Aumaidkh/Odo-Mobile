package com.hopcape.odo.core.domain.trip.model

import com.hopcape.odo.core.common.id.IdGenerator
import kotlin.jvm.JvmInline

/**
 * Typed identity for a [Trip]. A `value class` so it can never be confused with
 * a [com.hopcape.odo.core.domain.car.model.CarId] or a raw String.
 */
@JvmInline
value class TripId(val value: String) {
    companion object {
        /** Mint a fresh, client-side id (offline/optimistic insert). */
        fun new(ids: IdGenerator): TripId = TripId(ids.newId())
    }
}
