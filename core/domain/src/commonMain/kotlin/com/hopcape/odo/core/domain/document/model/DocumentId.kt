package com.hopcape.odo.core.domain.document.model

import com.hopcape.odo.core.common.id.IdGenerator
import kotlin.jvm.JvmInline

/**
 * Typed identity for a [Document]. A `value class` so it can never be confused with a
 * [com.hopcape.odo.core.domain.car.model.CarId], a service-log id, or a raw String.
 */
@JvmInline
value class DocumentId(val value: String) {
    companion object {
        /** Mint a fresh, client-side id (offline/optimistic insert). */
        fun new(ids: IdGenerator): DocumentId = DocumentId(ids.newId())
    }
}
