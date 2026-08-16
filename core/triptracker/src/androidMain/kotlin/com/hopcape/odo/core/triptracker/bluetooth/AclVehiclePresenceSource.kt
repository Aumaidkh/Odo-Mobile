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

    /**
     * Live collector count. The test harness waits on this before emitting: [onPresence]
     * drops the event when nothing collects yet (no replay, deliberately — see the KDoc
     * above), and the engine's own subscription starts asynchronously after enable.
     */
    internal val subscriptionCount get() = events.subscriptionCount

    override fun presence(): Flow<VehiclePresence> = events

    fun onPresence(presence: VehiclePresence) {
        events.tryEmit(presence)
    }

    private companion object {
        const val EVENT_BUFFER = 8
    }
}
