package com.hopcape.odo.feature.autoodometer.presentation.permissions

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.trip.model.TripMode
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TrackingStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBond
import com.hopcape.odo.core.triptracker.VehicleBondStore
import com.hopcape.odo.feature.autoodometer.domain.usecase.CompleteSetup
import com.hopcape.odo.feature.autoodometer.domain.usecase.EnrollTriggerDevice
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeAppSettingsRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTripRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FixedClock
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveRecentDrives
import com.hopcape.odo.feature.autoodometer.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.autoodometer.domain.usecase.testTrip
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.presentation.FakeActiveCarProvider
import com.hopcape.odo.feature.autoodometer.presentation.RecordingAnalytics
import com.hopcape.odo.feature.autoodometer.presentation.RecordingCrash
import com.hopcape.odo.feature.autoodometer.presentation.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PermissionSetupViewModelTest {

    private companion object {
        /** Fixed so the "Today" label and the drive ordering are not a function of the clock. */
        val NOW = Instant.parse("2026-08-19T10:00:00Z")
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Records which one ran first, for the ordering test. */
    private class OrderedBondStore(private val callOrder: MutableList<String>) : VehicleBondStore {
        val saved = mutableListOf<VehicleBond>()
        override suspend fun bond(): VehicleBond? = saved.lastOrNull()
        override suspend fun saveBond(bond: VehicleBond) {
            callOrder += "enroll"
            saved += bond
        }
        override suspend fun clearBond() = saved.clear()
    }

    private class OrderedTripTracker(private val callOrder: MutableList<String>) : TripTracker {
        var enabled = false
        var startIfConnectedCalls = 0
        override suspend fun setEnabled(enabled: Boolean) {
            callOrder += "complete_setup"
            this.enabled = enabled
        }
        override val isEnabled: Flow<Boolean> get() = flowOf(enabled)
        override val status: Flow<TrackingStatus> get() = flowOf(TrackingStatus.Disabled)
        override suspend fun pauseActiveTrip() = Unit
        override suspend fun resumeActiveTrip() = Unit
        override suspend fun discardActiveTrip() = Unit
        override suspend fun armFromPersistedState() = Unit
        override suspend fun startIfConnected() {
            startIfConnectedCalls++
        }
    }

    /** Bond store + trip tracker that also record which one ran first, for the ordering test. */
    private class OrderedFakes {
        val callOrder = mutableListOf<String>()
        val bonds = OrderedBondStore(callOrder)
        val tracker = OrderedTripTracker(callOrder)
    }

    private class Harness(
        val vm: PermissionSetupViewModel,
        val fakes: OrderedFakes,
        val analytics: RecordingAnalytics,
    )

    private fun harness(
        mode: TriggerMode,
        carId: CarId? = TEST_CAR,
        analytics: RecordingAnalytics = RecordingAnalytics(),
        trips: FakeTripRepository = FakeTripRepository(),
    ): Harness {
        val fakes = OrderedFakes()
        val vm = PermissionSetupViewModel(
            mode = mode,
            enrollTriggerDevice = EnrollTriggerDevice(bonds = fakes.bonds),
            completeSetup = CompleteSetup(tracker = fakes.tracker, settings = FakeAppSettingsRepository()),
            observeRecentDrives = ObserveRecentDrives(trips, FixedClock(NOW), TimeZone.UTC),
            activeCar = FakeActiveCarProvider(carId),
            telemetry = testTelemetry(analytics),
        )
        return Harness(vm, fakes, analytics)
    }

    @Test
    fun stereoMode_buildsTheTwoStepSequence() = runTest {
        // Notifications is not among them. It is a one-tap dialog raised on the way out of the
        // education screen, and counting it made this flow read one ask longer than it is.
        val h = harness(mode = TriggerMode.STEREO)

        assertEquals(
            listOf(PermissionSetupStep.FINE_LOCATION, PermissionSetupStep.BACKGROUND_LOCATION),
            h.vm.state.value.steps.map { it.step },
        )
    }

    @Test
    fun noStereoMode_buildsTheThreeStepSequence_withActivityRecognitionLast() = runTest {
        val h = harness(mode = TriggerMode.NO_STEREO)

        assertEquals(
            listOf(
                PermissionSetupStep.FINE_LOCATION,
                PermissionSetupStep.BACKGROUND_LOCATION,
                PermissionSetupStep.ACTIVITY_RECOGNITION,
            ),
            h.vm.state.value.steps.map { it.step },
        )
    }

    @Test
    fun stereoMode_allStepsGranted_completesWithoutEnrolling() = runTest {
        val h = harness(mode = TriggerMode.STEREO)

        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted))
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.BACKGROUND_LOCATION, PermissionStatus.Granted))

        assertTrue(h.fakes.bonds.saved.isEmpty(), "STEREO already enrolled at the device picker (F5)")
        assertTrue(h.fakes.tracker.enabled)
        assertEquals(1, h.fakes.tracker.startIfConnectedCalls)
        assertIs<PermissionSetupEffect.NavigateToGarage>(h.vm.effects.first())
    }

    @Test
    fun noStereoMode_allStepsGranted_enrollsThenCompletesSetup_inOrder() = runTest {
        val h = harness(mode = TriggerMode.NO_STEREO)

        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted))
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.BACKGROUND_LOCATION, PermissionStatus.Granted))
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.ACTIVITY_RECOGNITION, PermissionStatus.Granted))

        assertEquals(
            VehicleBond(carId = TEST_CAR, bluetoothId = "", triggerMode = TriggerMode.NO_STEREO),
            h.fakes.bonds.saved.single(),
        )
        assertTrue(h.fakes.tracker.enabled)
        assertEquals(listOf("enroll", "complete_setup"), h.fakes.callOrder)
        assertTrue(h.analytics.events.any { it.first == AutoOdometerTelemetry.Event.SETUP_COMPLETED })
        assertIs<PermissionSetupEffect.NavigateToGarage>(h.vm.effects.first())
    }

    @Test
    fun backgroundLocationDeclined_stillFinishesSetup() = runTest {
        // The one step the owner can turn down and still have a feature. `TriggerArming` arms
        // without it, so a drive taken with the app open is still measured — what is lost is the
        // drive nobody was there for, which the trip-logged card argues for again later.
        val h = harness(mode = TriggerMode.NO_STEREO)
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted))

        assertTrue(h.vm.state.value.showSkip, "background location offers a way past it")
        h.vm.onEvent(PermissionSetupEvent.SkipTapped)
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.ACTIVITY_RECOGNITION, PermissionStatus.Granted))

        assertEquals(TEST_CAR, h.fakes.bonds.saved.single().carId)
        assertTrue(h.fakes.tracker.enabled)
        assertIs<PermissionSetupEffect.NavigateToGarage>(h.vm.effects.first())
    }

    @Test
    fun fineLocationBlocked_doesNotComplete_andShowsTheDenialRow() = runTest {
        val h = harness(mode = TriggerMode.STEREO)

        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Blocked))

        assertEquals(PermissionSetupStep.FINE_LOCATION, h.vm.state.value.current?.step)
        assertTrue(h.vm.state.value.showDenialRow)
        assertTrue(h.vm.state.value.currentBlocked)
        assertFalse(h.vm.state.value.showSkip, "fine location is required — there is no way past it")
        assertFalse(h.fakes.tracker.enabled)
    }

    @Test
    fun backgroundLocationBlocked_staysOnTheStep_butStillOffersAWayPast() = runTest {
        val h = harness(mode = TriggerMode.STEREO)
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted))

        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.BACKGROUND_LOCATION, PermissionStatus.Blocked))

        assertEquals(PermissionSetupStep.BACKGROUND_LOCATION, h.vm.state.value.current?.step)
        assertTrue(h.vm.state.value.currentBlocked)
        assertTrue(h.vm.state.value.showSkip)
        assertFalse(h.fakes.tracker.enabled, "not finished until the owner says one way or the other")
    }

    @Test
    fun noStereo_activityRecognitionBlocked_doesNotComplete() = runTest {
        val h = harness(mode = TriggerMode.NO_STEREO)
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted))
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.BACKGROUND_LOCATION, PermissionStatus.Granted))

        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.ACTIVITY_RECOGNITION, PermissionStatus.Blocked))

        assertEquals(PermissionSetupStep.ACTIVITY_RECOGNITION, h.vm.state.value.current?.step)
        assertTrue(h.vm.state.value.currentBlocked)
        assertTrue(h.fakes.bonds.saved.isEmpty())
        assertFalse(h.fakes.tracker.enabled)
    }

    @Test
    fun skipTapped_onARequiredStep_isANoOp() = runTest {
        val h = harness(mode = TriggerMode.STEREO)
        assertEquals(PermissionSetupStep.FINE_LOCATION, h.vm.state.value.current?.step)

        h.vm.onEvent(PermissionSetupEvent.SkipTapped)

        assertEquals(PermissionSetupStep.FINE_LOCATION, h.vm.state.value.current?.step, "required steps can't be skipped")
    }

    @Test
    fun alreadyGrantedStep_onFirstRead_advancesWithoutATap() = runTest {
        val h = harness(mode = TriggerMode.STEREO)


        assertEquals(PermissionSetupStep.FINE_LOCATION, h.vm.state.value.current?.step)
    }

    @Test
    fun revocationMidFlow_reSurfacesTheAlreadyPassedStep() = runTest {
        val h = harness(mode = TriggerMode.NO_STEREO)
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted))
        assertEquals(PermissionSetupStep.BACKGROUND_LOCATION, h.vm.state.value.current?.step)

        // Fine location was revoked from system settings while the owner sat on the next step —
        // the checklist must not keep trusting the stale "granted" answer that let it move on.
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Askable))

        assertEquals(PermissionSetupStep.FINE_LOCATION, h.vm.state.value.current?.step)
        assertEquals(
            PermissionStatus.Askable,
            h.vm.state.value.steps.first { it.step == PermissionSetupStep.FINE_LOCATION }.status,
        )
        assertFalse(h.fakes.tracker.enabled, "must not have completed on the stale answer")
    }

    @Test
    fun noStereo_noActiveCar_doesNotEnrollOrCompleteSetup() = runTest {
        val h = harness(mode = TriggerMode.NO_STEREO, carId = null)

        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted))
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.BACKGROUND_LOCATION, PermissionStatus.Granted))
        h.vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.ACTIVITY_RECOGNITION, PermissionStatus.Granted))

        assertTrue(h.fakes.bonds.saved.isEmpty())
        assertFalse(h.fakes.tracker.enabled)
        assertTrue(h.analytics.events.any { it.first == AutoOdometerTelemetry.Event.NO_ACTIVE_CAR })
        assertFalse(h.vm.state.value.completing, "resets so the screen doesn't spin forever")
    }

    @Test
    fun backTapped_offTheDrawing_navigatesBack() = runTest {
        val h = harness(mode = TriggerMode.STEREO)

        h.vm.onEvent(PermissionSetupEvent.BackTapped)

        assertIs<PermissionSetupEffect.NavigateBack>(h.vm.effects.first())
    }

    /** A [VehicleBondStore] write that throws (e.g. an unavailable store) instead of saving. */
    private class ThrowingBondStore : VehicleBondStore {
        override suspend fun bond(): VehicleBond? = null
        override suspend fun saveBond(bond: VehicleBond) = error("vehicle bond store unavailable")
        override suspend fun clearBond() = Unit
    }

    /**
     * `EnrollTriggerDevice` has no `Either` wrapper — a thrown exception on the NO_STEREO
     * completion path is a genuine non-fatal, caught so the checklist resets to a retryable
     * state (`completing = false`) instead of leaving the owner on a screen that spins
     * forever, and `CompleteSetup`/`setupCompleted` never run on a bond that was never saved.
     */
    @Test
    fun noStereo_enrollThrows_recordsANonFatal_andResetsCompleting_withoutCompletingSetup() = runTest {
        val crash = RecordingCrash()
        val tracker = OrderedTripTracker(mutableListOf())
        val vm = PermissionSetupViewModel(
            mode = TriggerMode.NO_STEREO,
            enrollTriggerDevice = EnrollTriggerDevice(bonds = ThrowingBondStore()),
            completeSetup = CompleteSetup(tracker = tracker, settings = FakeAppSettingsRepository()),
            observeRecentDrives = ObserveRecentDrives(FakeTripRepository(), FixedClock(NOW), TimeZone.UTC),
            activeCar = FakeActiveCarProvider(TEST_CAR),
            telemetry = testTelemetry(crash = crash),
        )

        vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted))
        vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.BACKGROUND_LOCATION, PermissionStatus.Granted))
        vm.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.ACTIVITY_RECOGNITION, PermissionStatus.Granted))

        assertEquals(1, crash.recorded.size)
        assertFalse(tracker.enabled, "must not complete setup on a bond that failed to save")
        assertFalse(vm.state.value.completing)
    }
}
