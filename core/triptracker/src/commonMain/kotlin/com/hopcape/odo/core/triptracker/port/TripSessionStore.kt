package com.hopcape.odo.core.triptracker.port

import com.hopcape.odo.core.triptracker.model.SessionSnapshot

internal interface TripSessionStore {
    suspend fun save(snapshot: SessionSnapshot)
    suspend fun load(): SessionSnapshot?
    suspend fun clear()
}
