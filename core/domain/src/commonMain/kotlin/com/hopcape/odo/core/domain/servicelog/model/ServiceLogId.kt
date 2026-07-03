package com.hopcape.odo.core.domain.servicelog.model

import com.hopcape.odo.core.common.id.IdGenerator
import kotlin.jvm.JvmInline

/**
 * Typed identity for a [ServiceLogEntry]. A `value class` so it can never be
 * confused with a [com.hopcape.odo.core.domain.car.model.CarId] or a raw String.
 */
@JvmInline
value class ServiceLogId(val value: String) {
    companion object {
        /** Mint a fresh, client-side id (offline/optimistic insert). */
        fun new(ids: IdGenerator): ServiceLogId = ServiceLogId(ids.newId())
    }
}
