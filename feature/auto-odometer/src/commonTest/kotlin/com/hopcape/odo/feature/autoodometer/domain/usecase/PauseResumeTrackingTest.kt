package com.hopcape.odo.feature.autoodometer.domain.usecase

import com.hopcape.odo.core.domain.settings.model.AppSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Paused-until expiry: [PauseTracking] sets a week out; [ResumeTracking] clears it. */
class PauseResumeTrackingTest {

    private val now = Instant.parse("2026-08-07T00:00:00Z")

    @Test
    fun pauseTracking_setsPausedUntilAWeekOut_andDisablesTracking() = runTest {
        val settings = FakeAppSettingsRepository()
        val tracker = FakeTripTracker(enabled = true)
        val pause = PauseTracking(settings, tracker, FixedClock(now))

        val result = pause()

        assertTrue(result.isRight())
        assertEquals(now + 7.days, settings.settings.value.autoOdoPausedUntil)
        assertFalse(tracker.enabledFlow.value)
    }

    @Test
    fun resumeTracking_clearsPausedUntil_andEnablesTracking() = runTest {
        val pausedUntil = now + 7.days
        val settings = FakeAppSettingsRepository(AppSettings.Default.copy(autoOdoPausedUntil = pausedUntil))
        val tracker = FakeTripTracker(enabled = false)
        val resume = ResumeTracking(settings, tracker)

        val result = resume()

        assertTrue(result.isRight())
        assertNull(settings.settings.value.autoOdoPausedUntil)
        assertTrue(tracker.enabledFlow.value)
    }

    @Test
    fun resumeTracking_beforeExpiry_isAllowedAsAnExplicitAction() = runTest {
        // No scheduling exists (plan §4.1) — resuming early, before the week is up, is a
        // plain manual action with nothing to reconcile.
        val pausedUntil = now + 3.days
        val settings = FakeAppSettingsRepository(AppSettings.Default.copy(autoOdoPausedUntil = pausedUntil))
        val tracker = FakeTripTracker(enabled = false)
        val resume = ResumeTracking(settings, tracker)

        resume()

        assertNull(settings.settings.value.autoOdoPausedUntil)
        assertTrue(tracker.enabledFlow.value)
    }
}
