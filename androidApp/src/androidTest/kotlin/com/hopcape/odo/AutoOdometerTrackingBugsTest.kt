package com.hopcape.odo

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.common.FeatureFlags
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.triptracker.TrackingStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore
import com.hopcape.odo.core.triptracker.testing.TripTrackerTestHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Regressions for two tracking bugs reported from a real car (2026-08-16). Each test
 * asserts the expected behaviour; both failed against the engine as it stood and guard
 * the fixes now.
 *
 * **Report 1 — no auto-start.** "I connect to the stereo but the app is not in the
 * foreground, and tracking never starts." The engine's enabled flag was in-memory and
 * only ever set from UI paths, so after process death the receiver's presence event died
 * on a subscriber-less flow. Fixed by `TripTracker.armFromPersistedState` (called from
 * the app boot and from `BluetoothAclReceiver` itself) plus `replay = 1` on
 * `AclVehiclePresenceSource`. [stereoConnectStartsTracking_withoutAnyUiAction] models the
 * receiver-woken process: bond + persisted toggle exist, nothing in-process has enabled
 * the engine, and the ACL broadcast alone must lead to a tracking session.
 *
 * **Report 2 — walking counted as driving.** "I turn off the ignition, lock the car and
 * walk away, and my walk is added to the car's distance." `handlePendingStop` used to
 * integrate every fix through the whole stitch window; the session is now frozen at stop
 * entry (see `TripStateMachine`'s PENDING_STOP comment and
 * `TripStateMachineTest.pendingStopFix_leavesTheSessionFrozen`).
 * [walkingAwayAfterIgnitionOff_isNotCountedAsCarDistance] scripts drive + disconnect +
 * walk and asserts the recorded trip is the driven distance only.
 *
 * **Test infrastructure.** [TripTrackerTestHarness] (in `:core:triptracker` androidMain —
 * the ports are `internal`, so the bridge must live inside the module) scripts the three
 * signals a real car produces: ACL presence, motion transitions and the 1 Hz fix stream.
 * Fix timestamps are sample-time, not wall-clock, so a multi-kilometre drive scripts in
 * milliseconds. Everything else — Koin graph, engine, state machine, integrator,
 * finalizer, SQLite — is the shipped code.
 *
 * Same standing caveat as [AutoOdometerEndToEndTest]: clear app data first (no
 * migrations yet).
 */
@RunWith(AndroidJUnit4::class)
class AutoOdometerTrackingBugsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Same conditional shape as [AutoOdometerEndToEndTest] — see its KDoc. */
    @get:Rule
    val trackingPermissions: TestRule =
        if (FeatureFlags.AUTO_ODOMETER_ENABLED) {
            GrantPermissionRule.grant(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        } else {
            TestRule { base, _ -> base }
        }

    @Before
    fun startFromASetUpDevice() {
        // Before resetAutoOdometer(): reset resolves TripTracker, which constructs the
        // engine, which captures its LocationProvider — the scripted one must already be
        // the bound one by then.
        TripTrackerTestHarness.installScriptedLocation()
        resetAutoOdometer()
        seedOnboardedOwner()
        seedServiceHistory()
        installFakeBondedDeviceCatalog(listOf(defaultFakeDevice()))
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------ Report 1: stereo connect must auto-start ------------------------ */

    /**
     * The cold-start contract: an owner who enrolled once should never have to open the
     * app again for a trip to be tracked. The bond and persisted toggle are seeded
     * directly (enrolled in an earlier process life); nothing in this process calls
     * `setEnabled(true)` — exactly the state a receiver-woken process is in. The activity
     * the test rule launched is display-only here: no flow in it enables tracking.
     */
    @Test
    fun stereoConnectStartsTracking_withoutAnyUiAction() {
        assumeTrue(FeatureFlags.AUTO_ODOMETER_ENABLED)
        // The state CompleteSetup leaves behind: the bond and the persisted toggle — both
        // survive process death, and together they are what armFromPersistedState reads.
        runBlocking {
            GlobalContext.get().get<VehicleBondStore>()
                .saveBond(VehicleBond(CarId(LogFixtures.CAR), BOND_MAC, TriggerMode.STEREO))
            val settings = GlobalContext.get().get<AppSettingsRepository>()
            settings.save(settings.observe().first().copy(trackerEnabled = true))
        }

        // The real seam the OS wakes: the manifest receiver, MAC filter and all. Falls
        // back to the engine-level emit on an emulator image with no Bluetooth adapter —
        // both land in the same subscriber-less flow today.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (!TripTrackerTestHarness.fireAclConnected(context, BOND_MAC)) {
            TripTrackerTestHarness.connectStereo(BOND_MAC)
        }

        // Once the fix (whatever shape it takes) arms the engine, the connect must lead to
        // a fix request; the scripted drive then satisfies the speed gate.
        if (TripTrackerTestHarness.awaitFixCollector(timeoutMillis = 8_000)) {
            TripTrackerTestHarness.emitFixes(count = 20, speedMps = DRIVING_SPEED_MPS)
        }

        assertTrue(
            "Expected: enrolled stereo connecting starts tracking with no UI action, the way it " +
                "would after the OS killed the app overnight. Actual: tracking status stayed " +
                "${currentStatus()} — the engine is only ever enabled from UI flows, so the " +
                "receiver's presence event was dropped on a subscriber-less flow.",
            awaitTracking(timeoutMillis = 5_000),
        )
    }

    /* ------------------- Report 2: walking after ignition-off must not count ------------------- */

    /**
     * Drives [DRIVING_FIXES] seconds at [DRIVING_SPEED_MPS] (ground truth ~2.4 km),
     * disconnects the stereo (ignition off), then walks [WALKING_FIXES] seconds at
     * [WALKING_SPEED_MPS] (~360 m on foot, inside the 5-minute stitch window).
     * Disabling tracking at the end is only the flush — it finalizes whatever the session
     * holds, without waiting out the stitch timer.
     */
    @Test
    fun walkingAwayAfterIgnitionOff_isNotCountedAsCarDistance() {
        assumeTrue(FeatureFlags.AUTO_ODOMETER_ENABLED)

        // Enroll at the use-case level — the same two calls `CompleteSetup` makes. The UI
        // path is AutoOdometerEndToEndTest's subject, and on a fresh install (a gradle
        // connected run reinstalls with empty data) the app's start gate shows Welcome,
        // which would block this test on navigation it isn't about.
        runBlocking {
            GlobalContext.get().get<VehicleBondStore>().saveBond(
                VehicleBond(CarId(LogFixtures.CAR), AutoOdometerFixtures.DEVICE_ID, TriggerMode.STEREO),
            )
            GlobalContext.get().get<TripTracker>().setEnabled(true)
        }
        assertTrue(
            "The engine's presence collector never subscribed after enable — STEREO arming " +
                "needs BLUETOOTH_CONNECT and ACCESS_FINE_LOCATION, both granted by the rule.",
            TripTrackerTestHarness.awaitPresenceCollector(timeoutMillis = 5_000),
        )

        TripTrackerTestHarness.connectStereo(AutoOdometerFixtures.DEVICE_ID)
        assertTrue(
            "The engine never requested fixes after the stereo connected. " + TripTrackerTestHarness.debugState(),
            TripTrackerTestHarness.awaitFixCollector(timeoutMillis = 10_000),
        )

        TripTrackerTestHarness.emitFixes(count = DRIVING_FIXES, speedMps = DRIVING_SPEED_MPS)
        val drivenM = TripTrackerTestHarness.awaitTripDistanceSettled(timeoutMillis = 60_000)
        assertTrue(
            "Sanity: the scripted drive integrated only $drivenM m of the ~" +
                "${(DRIVING_FIXES * DRIVING_SPEED_MPS).toInt()} m ground truth — the fix " +
                "queue did not drain, so later phases would race it. " +
                TripTrackerTestHarness.debugState(),
            drivenM > (DRIVING_FIXES * DRIVING_SPEED_MPS * 0.8).toInt(),
        )
        TripTrackerTestHarness.disconnectStereo()
        TripTrackerTestHarness.emitFixes(count = WALKING_FIXES, speedMps = WALKING_SPEED_MPS)
        TripTrackerTestHarness.awaitTripDistanceSettled(timeoutMillis = 60_000)

        // The flush: finalize whatever the session holds without waiting out the stitch timer.
        runBlocking { GlobalContext.get().get<TripTracker>().setEnabled(false) }

        val recorded = awaitRecordedTripDistanceM(timeoutMillis = 15_000)
        assertNotNull("No trip was recorded at all after drive + disconnect + disable.", recorded)
        assertTrue(
            "Expected: the recorded trip holds only the driven distance ($drivenM m at " +
                "ignition-off, plus $DISTANCE_TOLERANCE_M m slack). Actual: $recorded m — the ~" +
                "${(WALKING_FIXES * WALKING_SPEED_MPS).toInt()} m walked after ignition-off was " +
                "integrated into the car's trip during the stitch window.",
            recorded!! <= drivenM + DISTANCE_TOLERANCE_M,
        )
    }

    /* ------------------------------ helpers ------------------------------ */

    private fun currentStatus(): TrackingStatus =
        runBlocking { GlobalContext.get().get<TripTracker>().status.first() }

    private fun awaitTracking(timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (currentStatus() is TrackingStatus.Tracking) return true
            Thread.sleep(100)
        }
        return currentStatus() is TrackingStatus.Tracking
    }

    private fun awaitRecordedTripDistanceM(timeoutMillis: Long): Long? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            recordedTripDistanceM()?.let { return it }
            Thread.sleep(200)
        }
        return recordedTripDistanceM()
    }

    private fun recordedTripDistanceM(): Long? = GlobalContext.get().get<SqlDriver>().executeQuery(
        identifier = null,
        sql = "SELECT distance_m FROM trips LIMIT 1",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
        parameters = 0,
    ).value

    private companion object {
        /** MAC-format on purpose — [TripTrackerTestHarness.fireAclConnected] mints a real [android.bluetooth.BluetoothDevice] from it. */
        const val BOND_MAC = "AA:BB:CC:11:22:33"

        const val DRIVING_SPEED_MPS = 12.0 // ~43 km/h, comfortably past the 8 km/h speed gate
        const val DRIVING_FIXES = 200 // 200 s of driving -> ground truth ~2_400 m
        const val WALKING_SPEED_MPS = 1.5 // brisk walk
        const val WALKING_FIXES = 240 // 4 min of walking, inside the 5-minute stitch window -> ~360 m

        const val DISTANCE_TOLERANCE_M = 100
    }
}
