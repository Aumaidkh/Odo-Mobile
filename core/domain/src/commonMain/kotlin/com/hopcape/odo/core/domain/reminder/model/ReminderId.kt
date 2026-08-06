package com.hopcape.odo.core.domain.reminder.model

import com.hopcape.odo.core.common.id.IdGenerator
import kotlin.jvm.JvmInline

/**
 * Typed identity for a [CustomReminder]. A `value class` so it can never be confused
 * with a [com.hopcape.odo.core.domain.car.model.CarId], a document id, or a raw String.
 */
@JvmInline
value class ReminderId(val value: String) {
    companion object {
        /** Mint a fresh, client-side id (offline/optimistic insert). */
        fun new(ids: IdGenerator): ReminderId = ReminderId(ids.newId())
    }
}
