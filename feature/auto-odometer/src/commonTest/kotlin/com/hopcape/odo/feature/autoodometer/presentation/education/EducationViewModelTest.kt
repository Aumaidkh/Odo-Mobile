package com.hopcape.odo.feature.autoodometer.presentation.education

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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The CTA's branch, which is the whole of this screen's logic.
 *
 * `POST_NOTIFICATIONS` used to be requested from the CTA itself, in the same breath as the
 * navigation, so Android's dialog landed on top of the Bluetooth rationale that had just been
 * pushed. The ask is a page of its own now and this screen only decides whether to show it.
 */
class EducationViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        mode: TriggerMode = TriggerMode.STEREO,
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ) = EducationViewModel(mode = mode, telemetry = testTelemetry(analytics)) to analytics

    @Test
    fun cta_whenTheSystemWouldStillPrompt_goesToTheNotificationStepFirst() = runTest {
        val (vm, _) = viewModel()
        vm.onEvent(EducationEvent.NotificationStatusObserved(PermissionStatus.Askable))

        vm.onEvent(EducationEvent.CtaTapped)

        val effect = assertIs<EducationEffect.NavigateToNotificationRationale>(vm.effects.first())
        assertEquals(TriggerMode.STEREO, effect.mode)
    }

    @Test
    fun cta_whenNotificationsAreAlreadyGranted_skipsStraightToThePicker() = runTest {
        val (vm, analytics) = viewModel()
        vm.onEvent(EducationEvent.NotificationStatusObserved(PermissionStatus.Granted))

        vm.onEvent(EducationEvent.CtaTapped)

        assertIs<EducationEffect.NavigateToDevicePicker>(vm.effects.first())
        assertTrue(analytics.events.any { it.first == AutoOdometerTelemetry.Event.NOTIFY_STEP_SKIPPED })
    }

    /**
     * Blocked means the system will not prompt again, so the only thing the step could offer is
     * a trip to the app's settings page — which is not what "Pair my car" promised.
     */
    @Test
    fun cta_whenNotificationsAreBlocked_skipsTheStepRatherThanSendingToSettings() = runTest {
        val (vm, _) = viewModel()
        vm.onEvent(EducationEvent.NotificationStatusObserved(PermissionStatus.Blocked))

        vm.onEvent(EducationEvent.CtaTapped)

        assertIs<EducationEffect.NavigateToDevicePicker>(vm.effects.first())
    }

    @Test
    fun cta_onTheNoStereoPath_stillEndsAtPermissionSetup() = runTest {
        val (vm, _) = viewModel(mode = TriggerMode.NO_STEREO)
        vm.onEvent(EducationEvent.NotificationStatusObserved(PermissionStatus.Granted))

        vm.onEvent(EducationEvent.CtaTapped)

        val effect = assertIs<EducationEffect.NavigateToPermissionSetup>(vm.effects.first())
        assertEquals(TriggerMode.NO_STEREO, effect.mode)
    }

    @Test
    fun cta_onTheNoStereoPath_alsoExplainsNotificationsFirstWhenAskable() = runTest {
        val (vm, _) = viewModel(mode = TriggerMode.NO_STEREO)
        vm.onEvent(EducationEvent.NotificationStatusObserved(PermissionStatus.Askable))

        vm.onEvent(EducationEvent.CtaTapped)

        val effect = assertIs<EducationEffect.NavigateToNotificationRationale>(vm.effects.first())
        assertEquals(TriggerMode.NO_STEREO, effect.mode)
    }

    @Test
    fun close_leavesTheFeature() = runTest {
        val (vm, _) = viewModel()

        vm.onEvent(EducationEvent.CloseTapped)

        assertIs<EducationEffect.NavigateBack>(vm.effects.first())
    }
}
