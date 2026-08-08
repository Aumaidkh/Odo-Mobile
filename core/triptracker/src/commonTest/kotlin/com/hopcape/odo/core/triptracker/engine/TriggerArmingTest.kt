package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.TriggerMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `(TriggerMode?, TrackingReadiness) -> ArmedTriggers` matrix (auto-odometer plan
 * §1.1/§6) — every combination, including the "no bond" and "missing permission stays
 * unarmed, no fallback to the other trigger" cases the plan calls out by name.
 */
class TriggerArmingTest {

    private val fullyReady = TrackingReadiness(
        fineLocation = true,
        backgroundLocation = true,
        activityRecognition = true,
        bluetoothConnect = true,
        notifications = true,
    )

    @Test
    fun noBond_armsNeitherTrigger() {
        val armed = TriggerArming.compute(null, fullyReady)
        assertEquals(ArmedTriggers(motion = false, presence = false), armed)
    }

    @Test
    fun stereo_fullyReady_armsOnlyPresence() {
        val armed = TriggerArming.compute(TriggerMode.STEREO, fullyReady)
        assertEquals(ArmedTriggers(motion = false, presence = true), armed)
    }

    @Test
    fun stereo_missingBluetoothConnect_staysUnarmed_doesNotFallBackToMotion() {
        val readiness = fullyReady.copy(bluetoothConnect = false)
        val armed = TriggerArming.compute(TriggerMode.STEREO, readiness)
        assertEquals(ArmedTriggers(motion = false, presence = false), armed)
    }

    @Test
    fun stereo_missingFineLocation_staysUnarmed_noSpeedGateWithoutFixes() {
        val readiness = fullyReady.copy(fineLocation = false)
        val armed = TriggerArming.compute(TriggerMode.STEREO, readiness)
        assertEquals(ArmedTriggers(motion = false, presence = false), armed)
    }

    @Test
    fun stereo_missingActivityRecognition_stillArmsPresence() {
        // STEREO never needed AR in the first place — irrelevant to this mode's arming.
        val readiness = fullyReady.copy(activityRecognition = false)
        val armed = TriggerArming.compute(TriggerMode.STEREO, readiness)
        assertEquals(ArmedTriggers(motion = false, presence = true), armed)
    }

    @Test
    fun noStereo_fullyReady_armsOnlyMotion() {
        val armed = TriggerArming.compute(TriggerMode.NO_STEREO, fullyReady)
        assertEquals(ArmedTriggers(motion = true, presence = false), armed)
    }

    @Test
    fun noStereo_missingActivityRecognition_staysUnarmed_doesNotFallBackToPresence() {
        val readiness = fullyReady.copy(activityRecognition = false)
        val armed = TriggerArming.compute(TriggerMode.NO_STEREO, readiness)
        assertEquals(ArmedTriggers(motion = false, presence = false), armed)
    }

    @Test
    fun noStereo_missingBluetoothOrLocation_stillArmsMotion() {
        // NO_STEREO never needed Bluetooth/fine-location to arm its own trigger.
        val readiness = fullyReady.copy(bluetoothConnect = false, fineLocation = false)
        val armed = TriggerArming.compute(TriggerMode.NO_STEREO, readiness)
        assertEquals(ArmedTriggers(motion = true, presence = false), armed)
    }

    @Test
    fun nothingReady_neitherModeArmsAnything() {
        val nothingReady = TrackingReadiness(
            fineLocation = false,
            backgroundLocation = false,
            activityRecognition = false,
            bluetoothConnect = false,
            notifications = false,
        )
        assertEquals(ArmedTriggers(motion = false, presence = false), TriggerArming.compute(TriggerMode.STEREO, nothingReady))
        assertEquals(ArmedTriggers(motion = false, presence = false), TriggerArming.compute(TriggerMode.NO_STEREO, nothingReady))
    }
}
