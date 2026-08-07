package com.hopcape.odo.core.triptracker.port

import com.hopcape.odo.core.triptracker.model.SessionSnapshot

/**
 * Public (unlike every other port here): the real implementation lives in
 * `:infrastructure:database` (S6), a separate Gradle module that has to see this type
 * to implement it — `internal` visibility does not cross module boundaries.
 */
interface TripSessionStore {
    suspend fun save(snapshot: SessionSnapshot)
    suspend fun load(): SessionSnapshot?
    suspend fun clear()
}
