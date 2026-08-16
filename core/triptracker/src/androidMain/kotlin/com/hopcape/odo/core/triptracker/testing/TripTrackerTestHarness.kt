package com.hopcape.odo.core.triptracker.testing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import com.hopcape.odo.core.triptracker.bluetooth.AclVehiclePresenceSource
import com.hopcape.odo.core.triptracker.bluetooth.BluetoothAclReceiver
import com.hopcape.odo.core.triptracker.engine.TripTrackerEngine
import com.hopcape.odo.core.triptracker.model.FixRequest
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.MotionKind
import com.hopcape.odo.core.triptracker.model.VehiclePresence
import com.hopcape.odo.core.triptracker.motion.TransitionMotionSource
import com.hopcape.odo.core.triptracker.port.LocationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Instrumented-test entry point into the tracking engine's signal seams.
 *
 * Every port the engine listens on is `internal` to this module — deliberately, features
 * must not reach the engine's internals — which also means `:androidApp`'s test sources
 * cannot fake them. This object is the one sanctioned bridge: it lives inside the module
 * so it can touch the internal types, and its public surface uses only platform and
 * primitive types so nothing internal leaks out through a signature.
 *
 * Nothing in the app calls this. It exists for `:androidApp`'s instrumented suites, which
 * drive real drives end to end without an emulator having Bluetooth, Play Services
 * activity recognition, or a scriptable fused-location feed:
 *
 * - [installScriptedLocation] swaps the Koin [LocationProvider] binding for a flow this
 *   object feeds. The engine resolves the provider on every RequestFixes (its constructor
 *   takes a provider function for exactly this reason), so installing it in `@Before` is
 *   effective even though the boot arm constructed the engine at app start.
 * - [connectStereo]/[disconnectStereo] emit presence exactly where
 *   [BluetoothAclReceiver] does, past the MAC filter — engine-level presence.
 * - [fireAclConnected]/[fireAclDisconnected] go through the real receiver instead,
 *   MAC filter and all — the cold-start entry point the OS wakes. They need a MAC-format
 *   id and fall back to the direct path on an emulator with no Bluetooth adapter.
 * - [emitFixes] scripts a drive: fixes 1 s apart in sample time (wall-clock-independent —
 *   the integrator measures sample timestamps, so a 5-minute drive scripts in
 *   milliseconds), advancing north at the given speed so Doppler and chord agree.
 * - [motion] emits a debounce-ready motion signal the way `ActivityTransitionReceiver`
 *   would (three identical signals clear the default debouncer).
 */
object TripTrackerTestHarness {

    private val scripted = ScriptedLocationProvider()
    private var installed = false

    /** Wall-clock and sample-time cursors, monotonic across the whole process run. */
    private var at: Instant = Clock.System.now()
    private var elapsed: Duration = 0.seconds
    private var lat: Double = 18.5204 // Pune; only deltas matter.
    private var lon: Double = 73.8567

    fun installScriptedLocation() {
        if (installed) return
        installed = true
        GlobalContext.get().loadModules(
            listOf(module { single<LocationProvider> { scripted } }),
            allowOverride = true,
        )
    }

    /** True once the engine is actually collecting fixes — emissions before that are dropped. */
    fun awaitFixCollector(timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (scripted.stream.subscriptionCount.value > 0) return true
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }

    /**
     * [count] fixes at [speedMps], 1 s apart in sample time, moving due north. Doppler
     * integrates speed x 1 s per fix, so the scripted ground truth is `count * speedMps`
     * metres (the first fix after a large speed change contributes ~0 — the integrator's
     * own acceleration guard).
     */
    fun emitFixes(count: Int, speedMps: Double, accuracyM: Double = 5.0) {
        runBlocking {
            repeat(count) {
                at += STEP
                elapsed += STEP
                lat += (speedMps * STEP.inWholeSeconds) / METERS_PER_DEGREE_LAT
                scripted.stream.emit(
                    LocationSample(
                        at = at,
                        elapsed = elapsed,
                        lat = lat,
                        lon = lon,
                        accuracyM = accuracyM.toFloat(),
                        speedMps = speedMps.toFloat(),
                    ),
                )
            }
        }
    }

    /**
     * True once the engine's presence collector is live. Enable starts that collector
     * asynchronously, and a presence event emitted before it subscribes is silently
     * dropped — real cars have human-scale gaps between enable and the next connect;
     * scripted tests do not.
     */
    fun awaitPresenceCollector(timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (presenceSource().subscriptionCount.value > 0) return true
            Thread.sleep(POLL_MILLIS)
        }
        return false
    }

    /**
     * Blocks until the live session distance stops changing for [settledForMillis].
     * [emitFixes] returns once its samples are buffered, not once the engine has folded
     * them in — a finalizing call (disable, disconnect-then-assert) issued straight after
     * would race the queue and read a partial distance. Returns the settled metres.
     */
    fun awaitTripDistanceSettled(timeoutMillis: Long, settledForMillis: Long = 2_000): Long {
        val engine = GlobalContext.get().get<TripTrackerEngine>()
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last = engine.distanceMeters.value
        var lastChange = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            val current = engine.distanceMeters.value
            if (current != last) {
                last = current
                lastChange = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - lastChange >= settledForMillis) {
                return current
            }
            Thread.sleep(POLL_MILLIS)
        }
        return last
    }

    /** One line of engine state for assertion messages — status, distance, live collectors. */
    fun debugState(): String {
        val koin = GlobalContext.get()
        val engine = koin.get<TripTrackerEngine>()
        return "status=${engine.status.value} distanceM=${engine.distanceMeters.value} " +
            "fixSubscribers=${scripted.stream.subscriptionCount.value} " +
            "presenceSubscribers=${presenceSource().subscriptionCount.value}"
    }

    /** Presence past the MAC filter — lands exactly where [BluetoothAclReceiver] emits. */
    fun connectStereo(bluetoothId: String) {
        presenceSource().onPresence(VehiclePresence.Connected(bluetoothId))
    }

    fun disconnectStereo() {
        presenceSource().onPresence(VehiclePresence.Disconnected)
    }

    /**
     * The real cold-start seam: builds the platform's ACL broadcast and hands it to a fresh
     * [BluetoothAclReceiver], the way the OS does when the car connects with the app dead.
     * [mac] must be colon-separated hex and must match the stored bond's `bluetoothId`, or
     * the receiver's own filter drops it — that filter is part of what's under test.
     *
     * Returns false when the device has no Bluetooth adapter to mint a [BluetoothDevice]
     * from (some emulator images); callers can fall back to [connectStereo].
     */
    fun fireAclConnected(context: Context, mac: String): Boolean = fireAcl(context, mac, BluetoothDevice.ACTION_ACL_CONNECTED)

    fun fireAclDisconnected(context: Context, mac: String): Boolean = fireAcl(context, mac, BluetoothDevice.ACTION_ACL_DISCONNECTED)

    /** One settled motion signal; [kind] is a [MotionKind] name, e.g. "IN_VEHICLE", "WALKING". */
    fun motion(kind: String) {
        GlobalContext.get().get<TransitionMotionSource>().onTransition(MotionKind.valueOf(kind), at)
    }

    private fun fireAcl(context: Context, mac: String, action: String): Boolean {
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: return false
        val device = try {
            adapter.getRemoteDevice(mac)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val intent = Intent(action).putExtra(BluetoothDevice.EXTRA_DEVICE, device)
        BluetoothAclReceiver().onReceive(context, intent)
        return true
    }

    private fun presenceSource(): AclVehiclePresenceSource = GlobalContext.get().get()

    private val STEP = 1.seconds
    private const val METERS_PER_DEGREE_LAT = 111_111.0
    private const val POLL_MILLIS = 50L
}

/**
 * The scripted stand-in for `FusedLocationProvider`. Zero buffer on purpose — a rendezvous:
 * `emit` suspends until a live collector takes the sample. The engine cancels and
 * re-subscribes its fix collector on every RequestFixes effect (trip start does this while
 * fixes are already flowing), and a buffered SharedFlow drops its queued values with the
 * old subscriber — the harness's whole drive vanished that way. With a rendezvous the
 * in-flight emit just parks across the resubscribe and nothing but at most the one
 * mid-cancel sample is lost.
 */
internal class ScriptedLocationProvider : LocationProvider {
    val stream = MutableSharedFlow<LocationSample>()

    override fun fixes(spec: FixRequest): Flow<LocationSample> = stream

    override suspend fun lastKnown(): LocationSample? = null
}
