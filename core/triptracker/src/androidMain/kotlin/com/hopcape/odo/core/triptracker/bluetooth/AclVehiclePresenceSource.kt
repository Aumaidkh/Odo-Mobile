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

    /**
     * `replay = 1` is the cold-start contract: [BluetoothAclReceiver] fires in a process
     * the OS may have just woken, and the engine's collector only subscribes once
     * `armFromPersistedState` has run — strictly after the broadcast. Without replay the
     * connect event that woke the process is dropped on a subscriber-less flow and the
     * drive is never tracked. A stale replayed `Connected` on a later re-enable is
     * harmless: the speed gate never confirms a parked car, and a stale `Disconnected`
     * is a no-op in Standby.
     */
    private val events = MutableSharedFlow<VehiclePresence>(replay = 1, extraBufferCapacity = EVENT_BUFFER)

    /**
     * Live collector count. The test harness waits on this before emitting a *sequence*
     * of events: replay keeps only the latest, and the engine's subscription starts
     * asynchronously after enable.
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
