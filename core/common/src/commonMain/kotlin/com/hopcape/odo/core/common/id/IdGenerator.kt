package com.hopcape.odo.core.common.id

/**
 * Port for generating unique identifiers on the client.
 *
 * IDs are minted client-side (not by the server) so the app can create rows
 * while offline and sync them later — the offline-first contract. Domain code
 * depends on this interface; a concrete implementation ([UuidIdGenerator], or a
 * test fake) is injected at the edges.
 */
fun interface IdGenerator {
    fun newId(): String
}
