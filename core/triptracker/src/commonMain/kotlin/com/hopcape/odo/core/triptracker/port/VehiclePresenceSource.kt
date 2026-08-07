package com.hopcape.odo.core.triptracker.port

import com.hopcape.odo.core.triptracker.model.VehiclePresence
import kotlinx.coroutines.flow.Flow

internal interface VehiclePresenceSource {
    fun presence(): Flow<VehiclePresence>
}
