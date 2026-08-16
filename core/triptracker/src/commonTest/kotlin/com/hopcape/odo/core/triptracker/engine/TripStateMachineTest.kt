package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.trip.model.GeoPoint
import com.hopcape.odo.core.domain.trip.model.ParkedLocation
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.triptracker.algorithm.GapClosed
import com.hopcape.odo.core.triptracker.algorithm.IntegrationResult
import com.hopcape.odo.core.triptracker.config.TripTrackerConfig
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.MotionKind
import com.hopcape.odo.core.triptracker.model.SessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TripStateMachineTest {

    private val config = TripTrackerConfig()
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val nearbyParked = ParkedLocation(CarId("car-1"), point(19.0761, 72.8778), t0)

    // ---- BT start happy path ----

    @Test
    fun btStartHappyPath_connectsThenSettlesInVehicle_thenPromotesOnNextEvent() {
        val step1 = go(TripPhase.Standby(), TripEvent.PresenceConnected, t0)
        // Connecting alone now also arms the speed-gate confirm path (§1.1) — fixes start
        // flowing immediately, even though this test's happy path promotes on Motion instead.
        assertEquals(TripPhase.Standby(presenceConnected = true), step1.newState)
        assertEquals(listOf(TripEffect.RequestFixes), step1.effects)

        val step2 = go(step1.newState, TripEvent.Motion(MotionKind.IN_VEHICLE), t0)
        assertEquals(
            TripPhase.Starting(TripMode.BT_VERIFIED, t0, attributionConfident = true),
            step2.newState,
        )
        assertEquals(
            listOf(
                TripEffect.StartForegroundSession,
                TripEffect.RequestFixes,
                TripEffect.Telemetry(TripTelemetryEvent.Started(TripMode.BT_VERIFIED)),
            ),
            step2.effects,
        )

        val fix = fixEvent(sample(t0 + 1.seconds), addedMeters = 50)
        val step3 = go(step2.newState, fix, t0 + 1.seconds)
        val tracking = assertIs<TripPhase.Tracking>(step3.newState)
        assertEquals(TripMode.BT_VERIFIED, tracking.session.mode)
        assertEquals(50L, tracking.session.distanceMeters)
    }

    // ---- GPS start requires 45s sustained + attribution ----

    @Test
    fun gpsStart_beforeSustainThreshold_doesNotStart() {
        val standby = TripPhase.Standby(gpsMotionSince = t0)
        val fix = fixEvent(sample(t0 + 10.seconds))
        val result = go(standby, fix, t0 + 10.seconds, parked = nearbyParked)
        assertEquals(standby, result.newState)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun gpsStart_afterSustainAndWithinAttributionRadius_starts() {
        val standby = TripPhase.Standby(gpsMotionSince = t0)
        val fix = fixEvent(sample(t0 + 46.seconds, lat = 19.0761, lon = 72.8778))
        val result = go(standby, fix, t0 + 46.seconds, parked = nearbyParked)
        assertEquals(
            TripPhase.Starting(TripMode.GPS_ONLY, t0 + 46.seconds, attributionConfident = true),
            result.newState,
        )
        assertEquals(
            listOf(
                TripEffect.StartForegroundSession,
                TripEffect.RequestFixes,
                TripEffect.Telemetry(TripTelemetryEvent.Started(TripMode.GPS_ONLY)),
            ),
            result.effects,
        )
    }

    @Test
    fun gpsStart_afterSustainButOutsideAttributionRadius_discardsCandidate() {
        val standby = TripPhase.Standby(gpsMotionSince = t0)
        val farAway = sample(t0 + 46.seconds, lat = 19.30, lon = 73.10)
        val result = go(standby, fixEvent(farAway), t0 + 46.seconds, parked = nearbyParked)
        assertEquals(TripPhase.Standby(gpsMotionSince = null), result.newState)
        assertEquals(listOf(TripEffect.StopFixes), result.effects)
    }

    // ---- BT glitch downgrade keeps trip ----

    @Test
    fun presenceLost_whileStillDebouncedInVehicle_downgradesInsteadOfStopping() {
        val tracking = TripPhase.Tracking(session(mode = TripMode.BT_VERIFIED, lastMotionKind = MotionKind.IN_VEHICLE))
        val result = go(tracking, TripEvent.PresenceLost, t0)
        val downgraded = assertIs<TripPhase.Tracking>(result.newState)
        assertEquals(TripMode.GPS_ONLY, downgraded.session.mode)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun presenceLost_withoutRecentInVehicleMotion_entersPendingStop() {
        val tracking = TripPhase.Tracking(session(mode = TripMode.BT_VERIFIED, lastMotionKind = MotionKind.STILL))
        val result = go(tracking, TripEvent.PresenceLost, t0)
        val pending = assertIs<TripPhase.PendingStop>(result.newState)
        assertEquals(t0 + config.stitchWindow, pending.stopDeadline)
        assertEquals(
            listOf(TripEffect.CancelTimer(TimerKind.IDLE), TripEffect.StartTimer(TimerKind.STITCH, config.stitchWindow)),
            result.effects,
        )
    }

    // ---- stitch resume within window continues same trip ----

    @Test
    fun stitchResumeWithinWindow_continuesTheSameSession() {
        val original = session(distanceMeters = 500)
        val pending = TripPhase.PendingStop(original, t0 + config.stitchWindow)
        val result = go(pending, TripEvent.PresenceConnected, t0 + 1.seconds)
        val resumed = assertIs<TripPhase.Tracking>(result.newState)
        assertEquals(original, resumed.session)
        assertEquals(
            listOf(TripEffect.CancelTimer(TimerKind.STITCH), TripEffect.Telemetry(TripTelemetryEvent.StitchResumed)),
            result.effects,
        )
    }

    // ---- pending stop freezes the session ----

    /**
     * The walking-after-ignition-off regression (field report, 2026-08-16): fixes that
     * arrive during the stitch window must not touch the session — not its distance (the
     * owner is on foot, not driving) and not its lastGoodFix (the parked spot must not
     * drift along with the walk). AutoOdometerTrackingBugsTest covers the same rule end
     * to end.
     */
    @Test
    fun pendingStopFix_leavesTheSessionFrozen() {
        val original = session(distanceMeters = 500)
        val pending = TripPhase.PendingStop(original, t0 + config.stitchWindow)

        val result = go(pending, fixEvent(sample(t0 + 10.seconds), addedMeters = 40), t0 + 10.seconds)

        assertEquals(pending, result.newState)
        assertTrue(result.effects.isEmpty())
    }

    // ---- stitch expiry finalizes ----

    @Test
    fun stitchExpiry_finalizesThenReturnsToStandby() {
        val original = session(distanceMeters = 500)
        val pending = TripPhase.PendingStop(original, t0 + config.stitchWindow)
        val finalizing = go(pending, TripEvent.StitchWindowExpired, t0 + config.stitchWindow)
        assertEquals(TripPhase.Finalizing(original), finalizing.newState)
        assertEquals(
            listOf(TripEffect.StopForegroundSession, TripEffect.StopFixes, TripEffect.Finalize(original)),
            finalizing.effects,
        )

        val backToStandby = go(finalizing.newState, TripEvent.Motion(MotionKind.STILL), t0)
        assertEquals(TripPhase.Standby(), backToStandby.newState)
        assertEquals(listOf(TripEffect.ClearSession), backToStandby.effects)
    }

    // ---- idle >= 20 min soft-pauses and motion resumes ----

    @Test
    fun idleTimeout_softPauses_andMotionResumes() {
        val tracking = TripPhase.Tracking(session(lastMotionKind = MotionKind.STILL), stopCandidateSince = t0)
        val paused = go(tracking, TripEvent.IdleTimeout, t0 + config.idleSoftPauseThreshold)
        assertEquals(TripPhase.SoftPaused(tracking.session), paused.newState)
        assertEquals(listOf(TripEffect.StopFixes), paused.effects)

        val resumed = go(paused.newState, TripEvent.Motion(MotionKind.IN_VEHICLE), t0 + config.idleSoftPauseThreshold + 5.seconds)
        val resumedTracking = assertIs<TripPhase.Tracking>(resumed.newState)
        assertEquals(MotionKind.IN_VEHICLE, resumedTracking.session.lastMotionKind)
        assertEquals(listOf(TripEffect.RequestFixes), resumed.effects)
    }

    @Test
    fun stillMotion_startsTheIdleTimer() {
        val tracking = TripPhase.Tracking(session())
        val result = go(tracking, TripEvent.Motion(MotionKind.STILL), t0)
        val updated = assertIs<TripPhase.Tracking>(result.newState)
        assertEquals(t0, updated.stopCandidateSince)
        assertEquals(listOf(TripEffect.StartTimer(TimerKind.IDLE, config.idleSoftPauseThreshold)), result.effects)
    }

    // ---- disable from every state releases everything ----

    @Test
    fun disable_fromStandby_releasesWithNoFinalize() {
        val result = go(TripPhase.Standby(gpsMotionSince = t0), TripEvent.Disabled, t0)
        assertEquals(TripPhase.Disabled, result.newState)
        assertEquals(
            listOf(
                TripEffect.StopForegroundSession,
                TripEffect.StopFixes,
                TripEffect.CancelTimer(TimerKind.IDLE),
                TripEffect.CancelTimer(TimerKind.STITCH),
                TripEffect.ClearSession,
            ),
            result.effects,
        )
    }

    @Test
    fun disable_fromEveryLivePhase_releasesAndFinalizesTheSession() {
        val theSession = session(distanceMeters = 1_234)
        val livePhases = listOf(
            TripPhase.Tracking(theSession),
            TripPhase.SoftPaused(theSession),
            TripPhase.SignalLost(theSession),
            TripPhase.PendingStop(theSession, t0 + config.stitchWindow),
            TripPhase.Finalizing(theSession),
        )

        for (phase in livePhases) {
            val result = go(phase, TripEvent.Disabled, t0)
            assertEquals(TripPhase.Disabled, result.newState, "phase=$phase")
            assertEquals(
                listOf(
                    TripEffect.StopForegroundSession,
                    TripEffect.StopFixes,
                    TripEffect.CancelTimer(TimerKind.IDLE),
                    TripEffect.CancelTimer(TimerKind.STITCH),
                    TripEffect.Finalize(theSession),
                    TripEffect.ClearSession,
                ),
                result.effects,
                "phase=$phase",
            )
        }
    }

    // ---- SessionRestored resumes or finalizes correctly ----

    @Test
    fun sessionRestored_tracking_resumesWithFixesAndForegroundSession() {
        val snapshot = snapshot(phase = "TRACKING", distanceMeters = 900)
        val result = go(TripPhase.Standby(), TripEvent.SessionRestored(snapshot), t0)
        val tracking = assertIs<TripPhase.Tracking>(result.newState)
        assertEquals(900L, tracking.session.distanceMeters)
        assertEquals(listOf(TripEffect.StartForegroundSession, TripEffect.RequestFixes), result.effects)
    }

    @Test
    fun sessionRestored_softPaused_resumesWithoutRequestingFixes() {
        val snapshot = snapshot(phase = "SOFT_PAUSED", distanceMeters = 900)
        val result = go(TripPhase.Standby(), TripEvent.SessionRestored(snapshot), t0)
        assertIs<TripPhase.SoftPaused>(result.newState)
        assertEquals(listOf(TripEffect.StartForegroundSession), result.effects)
    }

    @Test
    fun sessionRestored_pendingStopStillWithinWindow_resumesAndRestartsTheStitchTimer() {
        val deadline = t0 + 2.seconds
        val snapshot = snapshot(phase = "PENDING_STOP", distanceMeters = 900, stitchDeadline = deadline)
        val result = go(TripPhase.Standby(), TripEvent.SessionRestored(snapshot), t0)
        val pending = assertIs<TripPhase.PendingStop>(result.newState)
        assertEquals(deadline, pending.stopDeadline)
        assertEquals(
            listOf(TripEffect.StartForegroundSession, TripEffect.RequestFixes, TripEffect.StartTimer(TimerKind.STITCH, 2.seconds)),
            result.effects,
        )
    }

    @Test
    fun sessionRestored_pendingStopWindowAlreadyExpired_finalizesInstead() {
        val deadline = t0 - 5.seconds
        val snapshot = snapshot(phase = "PENDING_STOP", distanceMeters = 900, stitchDeadline = deadline)
        val result = go(TripPhase.Standby(), TripEvent.SessionRestored(snapshot), t0)
        val finalizing = assertIs<TripPhase.Finalizing>(result.newState)
        assertEquals(900L, finalizing.session.distanceMeters)
        assertEquals(listOf(TripEffect.Finalize(finalizing.session)), result.effects)
    }

    @Test
    fun sessionRestored_unrecognizedSnapshot_fallsBackToStandby() {
        val malformed = snapshot(phase = "NOT_A_REAL_PHASE", distanceMeters = 0)
        val result = go(TripPhase.Standby(), TripEvent.SessionRestored(malformed), t0)
        assertEquals(TripPhase.Standby(), result.newState)
        assertTrue(result.effects.isEmpty())
    }

    // ---- STEREO speed-gate start confirm (auto-odometer plan §1.1) ----

    @Test
    fun speedGate_presenceConnects_carStaysParked_neverStarts() {
        var state: TripPhase = TripPhase.Standby()
        val connect = go(state, TripEvent.PresenceConnected, t0)
        assertEquals(listOf(TripEffect.RequestFixes), connect.effects)
        state = connect.newState

        // The exact synthetic trace the plan's F1 verify step calls out: stereo connects,
        // car stays parked ~10 minutes — every fix reads 0 km/h, so the confirm counter
        // never advances no matter how long this goes on.
        repeat(600) { tick ->
            val fix = fixEvent(sample(t0 + (tick + 1).seconds, lat = 19.0, lon = 72.8), addedMeters = 0)
            val result = go(state, fix, t0 + (tick + 1).seconds)
            assertTrue(result.newState !is TripPhase.Starting, "started on a parked fix at tick=$tick")
            state = result.newState
        }
        assertEquals(TripPhase.Standby(presenceConnected = true, speedGateAboveCount = 0), state)
    }

    @Test
    fun speedGate_sustainedSpeedAboveThreshold_startsAfterConfirmFixes() {
        var state: TripPhase = go(TripPhase.Standby(), TripEvent.PresenceConnected, t0).newState

        // Below threshold — no progress.
        val slow = go(state, fixEvent(sample(t0 + 1.seconds, speedMps = 2f)), t0 + 1.seconds)
        assertIs<TripPhase.Standby>(slow.newState)
        state = slow.newState

        // config.speedGateConfirmFixes consecutive fixes above the threshold starts the trip.
        var result: Transition? = null
        for (i in 1..config.speedGateConfirmFixes) {
            val at = t0 + (1 + i).seconds
            result = go(state, fixEvent(sample(at, speedMps = 20f)), at)
            state = result.newState
        }
        val starting = assertIs<TripPhase.Starting>(state)
        assertEquals(TripMode.BT_VERIFIED, starting.mode)
        assertEquals(
            listOf(
                TripEffect.StartForegroundSession,
                TripEffect.RequestFixes,
                TripEffect.Telemetry(TripTelemetryEvent.Started(TripMode.BT_VERIFIED)),
            ),
            result!!.effects,
        )
    }

    @Test
    fun speedGate_belowThresholdFixResetsTheCount() {
        var state: TripPhase = go(TripPhase.Standby(), TripEvent.PresenceConnected, t0).newState
        state = go(state, fixEvent(sample(t0 + 1.seconds, speedMps = 20f)), t0 + 1.seconds).newState
        state = go(state, fixEvent(sample(t0 + 2.seconds, speedMps = 20f)), t0 + 2.seconds).newState
        assertEquals(TripPhase.Standby(presenceConnected = true, speedGateAboveCount = 2), state)

        // One slow fix resets the streak — the next speedGateConfirmFixes hits start over.
        state = go(state, fixEvent(sample(t0 + 3.seconds, speedMps = 1f)), t0 + 3.seconds).newState
        assertEquals(TripPhase.Standby(presenceConnected = true, speedGateAboveCount = 0), state)
    }

    // ---- pause / resume / discard (M5) ----

    @Test
    fun pauseRequested_whileTracking_softPausesAndReleasesFixes() {
        val tracking = TripPhase.Tracking(session(distanceMeters = 500))
        val result = go(tracking, TripEvent.PauseRequested, t0)
        assertEquals(TripPhase.SoftPaused(tracking.session), result.newState)
        assertEquals(listOf(TripEffect.StopFixes, TripEffect.CancelTimer(TimerKind.IDLE)), result.effects)
    }

    @Test
    fun resumeRequested_whileSoftPaused_returnsToTrackingAndRequestsFixes() {
        val paused = TripPhase.SoftPaused(session(distanceMeters = 500))
        val result = go(paused, TripEvent.ResumeRequested, t0)
        assertEquals(TripPhase.Tracking(paused.session), result.newState)
        assertEquals(listOf(TripEffect.RequestFixes), result.effects)
    }

    @Test
    fun discardRequested_whileTracking_releasesEverythingWithoutFinalizing() {
        val tracking = TripPhase.Tracking(session(distanceMeters = 500))
        val result = go(tracking, TripEvent.DiscardRequested, t0)
        assertEquals(TripPhase.Standby(), result.newState)
        assertEquals(
            listOf(
                TripEffect.StopForegroundSession,
                TripEffect.StopFixes,
                TripEffect.CancelTimer(TimerKind.IDLE),
                TripEffect.CancelTimer(TimerKind.STITCH),
                TripEffect.ClearSession,
            ),
            result.effects,
        )
        assertTrue(result.effects.none { it is TripEffect.Finalize }, "a discarded trip must never be finalized")
    }

    @Test
    fun discardRequested_fromStandby_isANoop() {
        val standby = TripPhase.Standby(presenceConnected = true)
        val result = go(standby, TripEvent.DiscardRequested, t0)
        assertEquals(standby, result.newState)
        assertTrue(result.effects.isEmpty())
    }

    // ---- helpers ----

    private fun go(
        state: TripPhase,
        event: TripEvent,
        now: Instant,
        parked: ParkedLocation? = null,
    ): Transition = TripStateMachine.transition(state, event, config, now, parked)

    private fun session(
        mode: TripMode = TripMode.GPS_ONLY,
        distanceMeters: Long = 0,
        lastMotionKind: MotionKind? = null,
    ) = TripSession(
        startedAt = t0,
        mode = mode,
        distanceMeters = distanceMeters,
        lastMotionKind = lastMotionKind,
        attributionConfident = true,
    )

    private fun point(lat: Double, lon: Double): GeoPoint = GeoPoint.of(lat, lon).getOrNull()!!

    private fun sample(at: Instant, lat: Double = 19.0, lon: Double = 72.8, speedMps: Float? = null) = LocationSample(
        at = at,
        elapsed = at - Instant.fromEpochSeconds(0),
        lat = lat,
        lon = lon,
        accuracyM = 10f,
        speedMps = speedMps,
    )

    private fun fixEvent(
        sample: LocationSample,
        addedMeters: Long = 0,
        gapOpened: Boolean = false,
        gapClosed: GapClosed? = null,
    ) = TripEvent.Fix(sample, IntegrationResult(addedMeters, gapOpened, gapClosed))

    private fun snapshot(phase: String, distanceMeters: Long, stitchDeadline: Instant? = null) = SessionSnapshot(
        phase = phase,
        carId = null,
        mode = TripMode.GPS_ONLY.name,
        startedAt = t0,
        distanceMeters = distanceMeters,
        estimatedMeters = 0,
        lastFix = null,
        stitchDeadline = stitchDeadline,
    )
}
