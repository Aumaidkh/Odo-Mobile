package com.hopcape.odo.core.triptracker.bluetooth

import com.hopcape.odo.core.triptracker.model.VehiclePresence
import com.hopcape.odo.core.triptracker.port.VehiclePresenceSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * ACL connect/disconnect broadcasts for the bonded device, as the [VehiclePresenceSource]
 * port. [BluetoothAclReceiver] does the MAC filtering and emits into [events] — this class
 * is the Koin-resolvable bridge between that manifest-declared receiver and the flow the
 * engine collects.
 */
internal class AclVehiclePresenceSource : VehiclePresenceSource {

    private val events = MutableSharedFlow<VehiclePresence>(extraBufferCapacity = EVENT_BUFFER)

    override fun presence(): Flow<VehiclePresence> = events

    fun onPresence(presence: VehiclePresence) {
        events.tryEmit(presence)
    }

    private companion object {
        const val EVENT_BUFFER = 8
    }
}
