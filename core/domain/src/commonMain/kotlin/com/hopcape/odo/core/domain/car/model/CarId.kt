package com.hopcape.odo.core.domain.car.model

import com.hopcape.odo.core.common.id.IdGenerator
import kotlin.jvm.JvmInline

/**
 * Typed identity for a [Car]. A `value class` so it can never be confused with
 * an [com.hopcape.odo.core.domain.owner.model.OwnerId] or a raw String.
 */
@JvmInline
value class CarId(val value: String) {
    companion object {
        /** Mint a fresh, client-side id (offline/optimistic insert). */
        fun new(ids: IdGenerator): CarId = CarId(ids.newId())
    }
}
