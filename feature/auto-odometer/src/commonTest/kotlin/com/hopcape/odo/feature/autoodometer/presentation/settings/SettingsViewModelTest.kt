package com.hopcape.odo.feature.autoodometer.presentation.settings

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.platform.notification.BackgroundStartAccess
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.feature.autoodometer.domain.usecase.DeleteAllTripData
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeAppSettingsRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeServiceLogRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTrackingPreconditions
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTripRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTripTracker
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeVehicleBondStore
import com.hopcape.odo.feature.autoodometer.domain.usecase.FixedClock
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveMonthlySummary
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveSetupState
import com.hopcape.odo.feature.autoodometer.domain.usecase.PauseTracking
import com.hopcape.odo.feature.autoodometer.domain.usecase.READY
import com.hopcape.odo.feature.autoodometer.domain.usecase.ResumeTracking
import com.hopcape.odo.feature.autoodometer.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.presentation.FakeActiveCarProvider
import com.hopcape.odo.feature.autoodometer.presentation.RecordingAnalytics
import com.hopcape.odo.feature.autoodometer.presentation.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** VM tests for the settings screen (M7): plan §7 F8's toggle, pause/expiry, delete-confirm and readiness list. */
class SettingsViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val now = Instant.parse("2026-08-07T00:00:00Z")

    private class Harness(
        val vm: SettingsViewModel,
        val tracker: FakeTripTracker,
        val trips: FakeTripRepository,
        val settings: FakeAppSettingsRepository,
        val analytics: RecordingAnalytics,
    )

    private fun harness(
        tracker: FakeTripTracker = FakeTripTracker(enabled = false),
        bonds: FakeVehicleBondStore = FakeVehicleBondStore(),
        trips: FakeTripRepository = FakeTripRepository(),
        settings: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        serviceLogs: FakeServiceLogRepository = FakeServiceLogRepository(),
        carId: CarId? = TEST_CAR,
        clock: FixedClock = FixedClock(now),
        analytics: RecordingAnalytics = RecordingAnalytics(),
        needsAutostart: Boolean = false,
    ): Harness {
        val vm = SettingsViewModel(
            tracker = tracker,
            observeSetupState = ObserveSetupState(
                serviceLogs = serviceLogs,
                bonds = bonds,
                tracker = tracker,
                preconditions = FakeTrackingPreconditions(READY),
            ),
            observeMonthlySummary = ObserveMonthlySummary(trips = trips, clock = clock, timeZone = TimeZone.UTC),
            pauseTracking = PauseTracking(settings = settings, tracker = tracker, clock = clock),
            resumeTracking = ResumeTracking(settings = settings, tracker = tracker),
            deleteAllTripData = DeleteAllTripData(trips = trips),
            settings = settings,
            backgroundStart = FakeBackgroundStart(needsAttention = needsAutostart),
            activeCar = FakeActiveCarProvider(carId),
            clock = clock,
            telemetry = testTelemetry(analytics),
            timeZone = TimeZone.UTC,
        )
        return Harness(vm, tracker, trips, settings, analytics)
    }

    @Test
    fun toggle_onOff_callsSetEnabled_andFiresTelemetryBothDirections() = runTest {
        val h = harness(tracker = FakeTripTracker(enabled = false))
        assertFalse(h.vm.state.value.trackingEnabled)

        h.vm.onEvent(SettingsEvent.ToggleTapped)
        assertTrue(h.tracker.enabledFlow.value)
        assertTrue(h.vm.state.value.trackingEnabled)
        assertEquals(true, h.analytics.last(AutoOdometerTelemetry.Event.TRACKING_TOGGLED)?.get(AutoOdometerTelemetry.Key.ON))

        h.vm.onEvent(SettingsEvent.ToggleTapped)
        assertFalse(h.tracker.enabledFlow.value)
        assertFalse(h.vm.state.value.trackingEnabled)
        assertEquals(false, h.analytics.last(AutoOdometerTelemetry.Event.TRACKING_TOGGLED)?.get(AutoOdometerTelemetry.Key.ON))
    }

    @Test
    fun pausedState_rendersPausedUntil_andResumeClearsItAndReEnables() = runTest {
        val pausedUntil = now + 7.days
        val settings = FakeAppSettingsRepository(AppSettings.Default.copy(autoOdoPausedUntil = pausedUntil))
        val tracker = FakeTripTracker(enabled = false)
        val h = harness(tracker = tracker, settings = settings)

        assertTrue(h.vm.state.value.isPaused)
        assertEquals(LocalDate(2026, 8, 14), h.vm.state.value.pausedUntil)

        h.vm.onEvent(SettingsEvent.ResumeTapped)

        assertFalse(h.vm.state.value.isPaused)
        assertNull(h.settings.settings.value.autoOdoPausedUntil)
        assertTrue(h.tracker.enabledFlow.value)
    }

    @Test
    fun pausedUntil_inThePast_rendersAsNotPaused() = runTest {
        val settings = FakeAppSettingsRepository(AppSettings.Default.copy(autoOdoPausedUntil = now - 1.days))
        val h = harness(settings = settings)

        assertFalse(h.vm.state.value.isPaused)
        assertNull(h.vm.state.value.pausedUntil)
    }

    @Test
    fun deleteTapped_doesNotDeleteImmediately_onlyAfterConfirm() = runTest {
        val trips = FakeTripRepository()
        val h = harness(trips = trips)

        h.vm.onEvent(SettingsEvent.DeleteTapped)
        assertTrue(h.vm.state.value.showDeleteConfirm)
        assertTrue(trips.deleteAllCalls.isEmpty())

        h.vm.onEvent(SettingsEvent.DeleteConfirmed)

        assertFalse(h.vm.state.value.showDeleteConfirm)
        assertEquals(listOf(TEST_CAR), trips.deleteAllCalls)
        assertTrue(h.analytics.events.any { it.first == AutoOdometerTelemetry.Event.TRIP_DATA_DELETED })
    }

    @Test
    fun deleteDismissed_cancelsWithoutDeletingAnything() = runTest {
        val trips = FakeTripRepository()
        val h = harness(trips = trips)

        h.vm.onEvent(SettingsEvent.DeleteTapped)
        h.vm.onEvent(SettingsEvent.DeleteDismissed)

        assertFalse(h.vm.state.value.showDeleteConfirm)
        assertTrue(trips.deleteAllCalls.isEmpty())
        assertTrue(h.analytics.events.none { it.first == AutoOdometerTelemetry.Event.TRIP_DATA_DELETED })
    }

    @Test
    fun readinessIssues_stereoBond_onlyFlagsFineBackgroundAndBluetooth_notActivityRecognition() = runTest {
        val bonds = FakeVehicleBondStore(VehicleBond(carId = TEST_CAR, bluetoothId = "bt-1", triggerMode = TriggerMode.STEREO))
        val h = harness(bonds = bonds)

        h.vm.onEvent(SettingsEvent.ReadinessChanged(READY.copy(bluetoothConnect = false, activityRecognition = false)))

        assertEquals(listOf(ReadinessIssue.BLUETOOTH_CONNECT), h.vm.state.value.readinessIssues)
    }

    @Test
    fun readinessIssues_noStereoBond_onlyFlagsFineBackgroundAndActivityRecognition_notBluetooth() = runTest {
        val bonds = FakeVehicleBondStore(VehicleBond(carId = TEST_CAR, bluetoothId = "", triggerMode = TriggerMode.NO_STEREO))
        val h = harness(bonds = bonds)

        h.vm.onEvent(SettingsEvent.ReadinessChanged(READY.copy(bluetoothConnect = false, activityRecognition = false)))

        assertEquals(listOf(ReadinessIssue.ACTIVITY_RECOGNITION), h.vm.state.value.readinessIssues)
    }

    @Test
    fun readinessIssues_empty_whenNoBondYet() = runTest {
        val h = harness(bonds = FakeVehicleBondStore(null))

        h.vm.onEvent(
            SettingsEvent.ReadinessChanged(READY.copy(fineLocation = false, bluetoothConnect = false, activityRecognition = false)),
        )

        assertTrue(h.vm.state.value.readinessIssues.isEmpty())
    }

    @Test
    fun preconditionLost_firesTelemetry_onlyOnATransitionFromGranted_notOnTheFirstReading() = runTest {
        val bonds = FakeVehicleBondStore(VehicleBond(carId = TEST_CAR, bluetoothId = "bt-1", triggerMode = TriggerMode.STEREO))
        val h = harness(bonds = bonds)

        // The baseline reading must not itself count as a loss.
        h.vm.onEvent(SettingsEvent.ReadinessChanged(READY))
        assertTrue(h.analytics.events.none { it.first == AutoOdometerTelemetry.Event.PRECONDITION_LOST })

        h.vm.onEvent(SettingsEvent.ReadinessChanged(READY.copy(fineLocation = false)))

        assertEquals(
            ReadinessIssue.FINE_LOCATION.name,
            h.analytics.last(AutoOdometerTelemetry.Event.PRECONDITION_LOST)?.get(AutoOdometerTelemetry.Key.WHICH),
        )
        assertEquals(listOf(ReadinessIssue.FINE_LOCATION), h.vm.state.value.readinessIssues)
    }

    @Test
    fun noActiveCar_doesNotLoad_butStillRecordsTelemetry() = runTest {
        val h = harness(carId = null)

        assertFalse(h.vm.state.value.loading)
        assertTrue(h.analytics.events.any { it.first == AutoOdometerTelemetry.Event.NO_ACTIVE_CAR })
    }

    @Test
    fun restrictiveManufacturer_withTrackingOn_showsTheAutostartAdvice() = runTest {
        val h = harness(tracker = FakeTripTracker(enabled = true), needsAutostart = true)
        advanceUntilIdle()

        assertTrue(h.vm.state.value.showAutostartAdvice)
    }

    /** Tracking is off, so this is advice about a problem the owner does not have yet. */
    @Test
    fun restrictiveManufacturer_withTrackingOff_staysQuiet() = runTest {
        val h = harness(tracker = FakeTripTracker(enabled = false), needsAutostart = true)
        advanceUntilIdle()

        assertFalse(h.vm.state.value.showAutostartAdvice)
    }

    @Test
    fun stockManufacturer_neverShowsTheAutostartAdvice() = runTest {
        val h = harness(tracker = FakeTripTracker(enabled = true), needsAutostart = false)
        advanceUntilIdle()

        assertFalse(h.vm.state.value.showAutostartAdvice)
    }

}

/** The manufacturer's autostart page, as the settings screen sees it. */
private class FakeBackgroundStart(private val needsAttention: Boolean) : BackgroundStartAccess {
    var openCount = 0
        private set

    override fun needsAttention(): Boolean = needsAttention

    override fun open(): Boolean {
        openCount++
        return true
    }
}
