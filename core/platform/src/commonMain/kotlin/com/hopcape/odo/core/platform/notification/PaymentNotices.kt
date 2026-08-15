package com.hopcape.odo.core.platform.notification

import com.hopcape.odo.core.domain.refuel.PaymentNotice
import com.hopcape.odo.core.domain.refuel.PaymentNoticeSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The seam between the platform's notification callback and the app's detection.
 *
 * The listener service is constructed by the operating system, so it cannot be given
 * dependencies and cannot be a Koin definition. This object is what the two halves share: the
 * service publishes into it, and the collector — an ordinary suspending worker with the
 * repositories it needs — reads out of it.
 *
 * It is also where the allow-list lives, because the service has to consult it inside a
 * callback that cannot suspend. [setWatchedPackages] is called whenever the owner's settings
 * change; until it is, nothing is watched, which is the correct state for a phone whose owner
 * has enabled nothing.
 *
 * **No replay and no buffering.** A notice nobody was listening for is not one to surface an
 * hour later — the owner has driven away, and a fill that appears long after the payment is a
 * confusing thing to confirm. Dropping the oldest under pressure is likewise deliberate: a
 * backlog of notices means detection is not keeping up, and the recent ones are the ones worth
 * having.
 */
object PaymentNotices : PaymentNoticeSource {

    private val notices = MutableSharedFlow<PaymentNotice>(
        replay = 0,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Volatile
    private var watched: Set<String> = emptySet()

    /**
     * Whether the OS currently has the listener bound.
     *
     * Granted and bound are different things, and only the service itself is ever told which
     * one is true — the system reports the connection to it and to nothing else. Recording it
     * here is what lets the rest of the app tell "the owner never granted this" apart from
     * "the grant is there and nothing is being delivered".
     *
     * False on a fresh process, before the OS has bound anything. A reader has to allow for
     * that rather than treating it as a fault.
     */
    @Volatile
    private var connected: Boolean = false

    override fun notices(): Flow<PaymentNotice> = notices.asSharedFlow()

    /** Publish a notice the service read. Never suspends — it is called from a callback. */
    fun publish(notice: PaymentNotice) {
        notices.tryEmit(notice)
    }

    /**
     * Which packages the service may read, as the owner's settings currently stand.
     *
     * Held here rather than read from storage inside the callback: `onNotificationPosted` runs
     * for every notification the phone shows, and a database read on that path would be a
     * query per notification all day.
     */
    fun setWatchedPackages(packages: Set<String>) {
        watched = packages
    }

    /** Whether [packageName] is one the owner enabled. False until settings say otherwise. */
    fun isWatched(packageName: String): Boolean = packageName in watched

    /** Called by the service as the system connects and disconnects it. */
    fun markConnected(value: Boolean) {
        connected = value
    }

    /** Whether the listener is bound right now. */
    fun isConnected(): Boolean = connected

    /**
     * What the running service does when asked to let go.
     *
     * Installed by the service itself as it connects, because `requestUnbind` is an instance
     * method and only the instance the system constructed can call it. Cleared on disconnect,
     * so a stale reference cannot be invoked against a service that is already gone.
     */
    @Volatile
    private var unbind: (() -> Unit)? = null

    fun onUnbindRequested(action: (() -> Unit)?) {
        unbind = action
    }

    /** Ask the service to release its connection. A no-op when nothing is bound. */
    fun requestUnbind() {
        unbind?.invoke()
    }

    /**
     * Forget the allow-list.
     *
     * Called when detection is switched off and on sign-out. The service may still be bound by
     * the OS at that moment, and an empty list is what makes it read nothing in the meantime.
     */
    fun clear() {
        watched = emptySet()
        connected = false
    }

    /** A few notices' worth of slack for a slow collector, not a queue. */
    private const val BUFFER = 8
}
