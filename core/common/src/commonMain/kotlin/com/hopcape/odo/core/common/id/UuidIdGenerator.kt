package com.hopcape.odo.core.common.id

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Default [IdGenerator] backed by a random (v4) UUID.
 *
 * Multiplatform via `kotlin.uuid` — no expect/actual needed. UUIDs are random
 * enough to mint offline without coordinating with the server.
 */
@OptIn(ExperimentalUuidApi::class)
class UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
