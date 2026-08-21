package com.hopcape.odo.feature.autoodometer.presentation.notifications

import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry
import com.hopcape.odo.feature.autoodometer.presentation.RecordingAnalytics
import com.hopcape.odo.feature.autoodometer.presentation.testTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationRationaleViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        mode: TriggerMode = TriggerMode.STEREO,
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = NotificationRationaleViewModel(mode = mode, telemetry = testTelemetry(analytics)) to analytics

    @Test
    fun allow_raisesTheSystemDialog_ratherThanNavigating() = runTest {
        val (vm, _) = viewModel()

        vm.onEvent(NotificationRationaleEvent.AllowTapped)

        assertIs<NotificationRationaleEffect.RequestPermission>(vm.effects.first())
        assertTrue(vm.state.value.asked)
    }

    /**
     * The status arrives once on entry with the same `Askable` the screen exists to act on.
     * Advancing on that would make the step flash past before the owner could read it.
     */
    @Test
    fun theStatusReadOnEntry_doesNotSkipTheStep() = runTest {
        val (vm, _) = viewModel()

        vm.onEvent(NotificationRationaleEvent.StatusObserved(PermissionStatus.Askable))

        assertFalse(vm.state.value.asked)
    }

    @Test
    fun answeringTheDialog_movesOnToThePicker() = runTest {
        val (vm, analytics) = viewModel()
        vm.onEvent(NotificationRationaleEvent.AllowTapped)

        vm.onEvent(NotificationRationaleEvent.StatusObserved(PermissionStatus.Granted))

        assertTrue(analytics.events.any { it.first == AutoOdometerTelemetry.Event.PERMISSION_ANSWERED })
    }

    /** A refusal costs the live distance and its controls, never the feature — so it moves on too. */
    @Test
    fun refusingTheDialog_movesOnAllTheSame() = runTest {
        val (vm, _) = viewModel()
        vm.onEvent(NotificationRationaleEvent.AllowTapped)
        vm.effects.first() // the RequestPermission handoff

        vm.onEvent(NotificationRationaleEvent.StatusObserved(PermissionStatus.Blocked))

        assertIs<NotificationRationaleEffect.NavigateToDevicePicker>(vm.effects.first())
    }

    /** Backing out of the system dialog leaves the status where it was — the owner stays here. */
    @Test
    fun dismissingTheDialogWithoutAnswering_leavesTheOwnerOnTheStep() = runTest {
        val (vm, _) = viewModel()
        vm.onEvent(NotificationRationaleEvent.AllowTapped)
        vm.effects.first() // the RequestPermission handoff

        vm.onEvent(NotificationRationaleEvent.StatusObserved(PermissionStatus.Askable))

        vm.onEvent(NotificationRationaleEvent.SkipTapped)
        assertIs<NotificationRationaleEffect.NavigateToDevicePicker>(
            vm.effects.first(),
            "the only effect waiting is the skip's — the unanswered dialog produced none",
        )
    }

    @Test
    fun skip_movesOnWithoutAsking() = runTest {
        val (vm, _) = viewModel()

        vm.onEvent(NotificationRationaleEvent.SkipTapped)

        assertIs<NotificationRationaleEffect.NavigateToDevicePicker>(vm.effects.first())
        assertFalse(vm.state.value.asked)
    }

    @Test
    fun onTheNoStereoPath_movesOnToPermissionSetup() = runTest {
        val (vm, _) = viewModel(mode = TriggerMode.NO_STEREO)

        vm.onEvent(NotificationRationaleEvent.SkipTapped)

        val effect = assertIs<NotificationRationaleEffect.NavigateToPermissionSetup>(vm.effects.first())
        assertEquals(TriggerMode.NO_STEREO, effect.mode)
    }

    @Test
    fun back_returnsToTheExplainer() = runTest {
        val (vm, _) = viewModel()

        vm.onEvent(NotificationRationaleEvent.BackTapped)

        assertIs<NotificationRationaleEffect.NavigateBack>(vm.effects.first())
    }
}
