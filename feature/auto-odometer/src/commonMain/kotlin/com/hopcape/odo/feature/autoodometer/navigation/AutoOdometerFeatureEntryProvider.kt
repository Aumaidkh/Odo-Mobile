package com.hopcape.odo.feature.autoodometer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.bluetooth.rememberBluetoothEnabler
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.platform.permission.PlatformPermission
import com.hopcape.odo.core.platform.permission.rememberPermissionController
import com.hopcape.odo.core.triptracker.TrackingReadiness
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.presentation.devicepicker.BluetoothRationaleScreen
import com.hopcape.odo.feature.autoodometer.presentation.devicepicker.DevicePickerEffect
import com.hopcape.odo.feature.autoodometer.presentation.devicepicker.DevicePickerEvent
import com.hopcape.odo.feature.autoodometer.presentation.devicepicker.DevicePickerScreen
import com.hopcape.odo.feature.autoodometer.presentation.devicepicker.DevicePickerViewModel
import com.hopcape.odo.feature.autoodometer.presentation.education.EducationEffect
import com.hopcape.odo.feature.autoodometer.presentation.education.EducationEvent
import com.hopcape.odo.feature.autoodometer.presentation.education.EducationScreen
import com.hopcape.odo.feature.autoodometer.presentation.education.EducationViewModel
import com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupEffect
import com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupEvent
import com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupScreen
import com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupStep
import com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupViewModel
import com.hopcape.odo.feature.autoodometer.presentation.settings.ReadinessIssue
import com.hopcape.odo.feature.autoodometer.presentation.settings.SettingsEffect
import com.hopcape.odo.feature.autoodometer.presentation.settings.SettingsEvent
import com.hopcape.odo.feature.autoodometer.presentation.settings.SettingsScreen
import com.hopcape.odo.feature.autoodometer.presentation.settings.SettingsViewModel
import com.hopcape.odo.feature.autoodometer.presentation.triplogged.TripLoggedEffect
import com.hopcape.odo.feature.autoodometer.presentation.triplogged.TripLoggedEvent
import com.hopcape.odo.feature.autoodometer.presentation.triplogged.TripLoggedScreen
import com.hopcape.odo.feature.autoodometer.presentation.triplogged.TripLoggedViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Auto-odometer's contribution to the navigation graph: the [OdoDestination.AutoOdometer]
 * group. Collected by the `:app` host via `getAll<FeatureEntryProvider>()`.
 *
 * F4 replaced the education placeholder with the real screen; F5 replaced the device
 * picker; F6 replaced permission setup; F7 replaced the trip-logged moment; F8 replaced
 * settings (docs/AUTO_ODOMETER_PLAN.md §7).
 */
internal class AutoOdometerFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.AutoOdometer.Education> { key -> AutoOdometerEducationRoute(key, navigationManager) }
        entry<OdoDestination.AutoOdometer.DevicePicker> { AutoOdometerDevicePickerRoute(navigationManager) }
        entry<OdoDestination.AutoOdometer.PermissionSetup> { key ->
            AutoOdometerPermissionSetupRoute(key, navigationManager)
        }
        entry<OdoDestination.AutoOdometer.TripLogged> { key -> AutoOdometerTripLoggedRoute(key, navigationManager) }
        entry<OdoDestination.AutoOdometer.Settings> { AutoOdometerSettingsRoute(navigationManager) }
    }
}

/**
 * M2 — the how-it-works + privacy explainer. `mode` is mapped from the nav-local
 * [OdoDestination.AutoOdometer.AutoOdometerFlowMode] to `:core:triptracker`'s
 * [TriggerMode] here, at the boundary, so the ViewModel and every use case downstream
 * speak the one domain-shaped enum (docs/AUTO_ODOMETER_PLAN.md's locked flow decision).
 */
@Composable
internal fun AutoOdometerEducationRoute(
    key: OdoDestination.AutoOdometer.Education,
    navigationManager: NavigationManager,
) {
    val mode = key.mode.toTriggerMode()
    val viewModel = koinViewModel<EducationViewModel> { parametersOf(mode) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // `POST_NOTIFICATIONS` has no page of its own. It is a one-tap dialog and this screen's
    // third numbered line — "your odometer ticks up when you park" — is the notification it is
    // for, so it is asked for on the way out of here rather than as a step of the checklist.
    // Only when the system would actually prompt: hijacking "pair my car" into a trip to app
    // settings is not a fair reading of that button.
    val notifications = rememberPermissionController(PlatformPermission.POST_NOTIFICATIONS)

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            EducationEffect.NavigateToDevicePicker ->
                navigationManager.navigateTo(OdoDestination.AutoOdometer.DevicePicker)

            is EducationEffect.NavigateToPermissionSetup ->
                navigationManager.navigateTo(
                    OdoDestination.AutoOdometer.PermissionSetup(mode = effect.mode.toFlowMode()),
                )

            EducationEffect.NavigateBack -> navigationManager.back()
        }
    }

    EducationScreen(
        state = state,
        onCtaClick = {
            if (notifications.status == PermissionStatus.Askable) notifications.request()
            viewModel.onEvent(EducationEvent.CtaTapped)
        },
        onClose = { viewModel.onEvent(EducationEvent.CloseTapped) },
    )
}

private fun OdoDestination.AutoOdometer.AutoOdometerFlowMode.toTriggerMode(): TriggerMode = when (this) {
    OdoDestination.AutoOdometer.AutoOdometerFlowMode.STEREO -> TriggerMode.STEREO
    OdoDestination.AutoOdometer.AutoOdometerFlowMode.NO_STEREO -> TriggerMode.NO_STEREO
}

private fun TriggerMode.toFlowMode(): OdoDestination.AutoOdometer.AutoOdometerFlowMode = when (this) {
    TriggerMode.STEREO -> OdoDestination.AutoOdometer.AutoOdometerFlowMode.STEREO
    TriggerMode.NO_STEREO -> OdoDestination.AutoOdometer.AutoOdometerFlowMode.NO_STEREO
}

/**
 * M3 — the trigger-device picker. The `BLUETOOTH_CONNECT` controller is read here, the same
 * way the camera permission is read at `BillScanRoute` — a composable, not an injected port
 * — and folded into the ViewModel's state via [DevicePickerEvent.PermissionChanged].
 *
 * [rememberBluetoothEnabler] is here for the same reason: putting the system's turn-on dialog
 * on screen needs the Activity. The radio's *state* is not read here at all — that is a plain
 * flow the ViewModel collects, so switching Bluetooth on from the notification shade reaches
 * the screen whether or not it recomposed.
 */
@Composable
internal fun AutoOdometerDevicePickerRoute(navigationManager: NavigationManager) {
    val viewModel: DevicePickerViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberPermissionController(PlatformPermission.BLUETOOTH_CONNECT)
    val bluetoothEnabler = rememberBluetoothEnabler()

    LaunchedEffect(permission.status) {
        viewModel.onEvent(DevicePickerEvent.PermissionChanged(permission.status))
    }

    val grant = {
        if (permission.status == PermissionStatus.Blocked) {
            permission.openAppSettings()
        } else {
            permission.request()
        }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            DevicePickerEffect.RequestBluetoothEnable -> bluetoothEnabler.request()

            DevicePickerEffect.NavigateToPermissionSetup -> navigationManager.navigateTo(
                OdoDestination.AutoOdometer.PermissionSetup(
                    mode = OdoDestination.AutoOdometer.AutoOdometerFlowMode.STEREO,
                ),
            )

            DevicePickerEffect.NavigateToNoStereoEducation -> navigationManager.navigateTo(
                OdoDestination.AutoOdometer.Education(
                    mode = OdoDestination.AutoOdometer.AutoOdometerFlowMode.NO_STEREO,
                ),
            )

            DevicePickerEffect.NavigateBack -> navigationManager.back()
        }
    }

    if (state.showRationale) {
        BluetoothRationaleScreen(
            blocked = state.permissionBlocked,
            onAllow = grant,
            onNotNow = { viewModel.onEvent(DevicePickerEvent.PermissionDeclined) },
            onBack = { viewModel.onEvent(DevicePickerEvent.BackTapped) },
        )
        return
    }

    DevicePickerScreen(
        state = state,
        onDeviceSelected = { viewModel.onEvent(DevicePickerEvent.DeviceSelected(it)) },
        onUseTapped = { viewModel.onEvent(DevicePickerEvent.UseTapped) },
        onNoBluetoothTapped = { viewModel.onEvent(DevicePickerEvent.NoBluetoothTapped) },
        onGrantPermission = grant,
        onTurnOnBluetooth = { viewModel.onEvent(DevicePickerEvent.TurnOnBluetoothTapped) },
        onBluetoothSheetConfirm = { viewModel.onEvent(DevicePickerEvent.BluetoothSheetConfirmed) },
        onBluetoothSheetDismiss = { viewModel.onEvent(DevicePickerEvent.BluetoothSheetDismissed) },
        onBack = { viewModel.onEvent(DevicePickerEvent.BackTapped) },
    )
}

/**
 * M4 — the staged permission checklist. Every step's [com.hopcape.odo.core.platform.permission.PermissionController]
 * is read here, at the route host, the same way [AutoOdometerDevicePickerRoute] reads
 * `BLUETOOTH_CONNECT` — a composable, not an injected port — and each one's status is folded
 * into the ViewModel via [PermissionSetupEvent.StatusObserved]. The route also decides what the
 * primary CTA actually triggers for the step on screen (a system dialog, or the settings page
 * once blocked); the ViewModel only sequences which step that is.
 */
@Composable
internal fun AutoOdometerPermissionSetupRoute(
    key: OdoDestination.AutoOdometer.PermissionSetup,
    navigationManager: NavigationManager,
) {
    val mode = key.mode.toTriggerMode()
    val viewModel = koinViewModel<PermissionSetupViewModel> { parametersOf(mode) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val location = rememberPermissionController(PlatformPermission.ACCESS_FINE_LOCATION)
    // Both modes: canTrack requires it, and it is what lets a trip start with the app
    // closed. Its step sits strictly after FINE_LOCATION — Android refuses a combined ask.
    val backgroundLocation = rememberPermissionController(PlatformPermission.ACCESS_BACKGROUND_LOCATION)
    val activityRecognition = if (mode == TriggerMode.NO_STEREO) {
        rememberPermissionController(PlatformPermission.ACTIVITY_RECOGNITION)
    } else {
        null
    }

    LaunchedEffect(location.status) {
        viewModel.onEvent(PermissionSetupEvent.StatusObserved(PermissionSetupStep.FINE_LOCATION, location.status))
    }
    LaunchedEffect(backgroundLocation.status) {
        viewModel.onEvent(
            PermissionSetupEvent.StatusObserved(PermissionSetupStep.BACKGROUND_LOCATION, backgroundLocation.status),
        )
    }
    if (activityRecognition != null) {
        LaunchedEffect(activityRecognition.status) {
            viewModel.onEvent(
                PermissionSetupEvent.StatusObserved(PermissionSetupStep.ACTIVITY_RECOGNITION, activityRecognition.status),
            )
        }
    }

    fun controllerFor(step: PermissionSetupStep) = when (step) {
        PermissionSetupStep.FINE_LOCATION -> location
        PermissionSetupStep.BACKGROUND_LOCATION -> backgroundLocation
        PermissionSetupStep.ACTIVITY_RECOGNITION ->
            activityRecognition ?: error("no ACTIVITY_RECOGNITION controller mounted for $mode")
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            PermissionSetupEffect.NavigateBack -> navigationManager.back()

            // The ask itself, decided by the ViewModel and performed here because only a
            // composable can hold the controller. Which page the owner pressed on is the
            // ViewModel's business; whether that ends in a dialog or the settings page is this
            // layer's, because only the controller knows the system will not prompt again.
            is PermissionSetupEffect.RequestPermission -> {
                val controller = controllerFor(effect.step)
                if (effect.blocked) controller.openAppSettings() else controller.request()
            }

            // Clears the whole education/picker/permissions flow off the back stack rather than
            // a single pop, which would land on the picker or education instead of the garage
            // tab (docs/AUTO_ODOMETER_PLAN.md's locked navigation-flow decision).
            PermissionSetupEffect.NavigateToGarage -> navigationManager.navigateTo(
                destination = OdoDestination.Garage.Home,
                popUpTo = OdoDestination.Garage.Home,
            )
        }
    }

    PermissionSetupScreen(
        state = state,
        onContinue = { viewModel.onEvent(PermissionSetupEvent.ContinueTapped) },
        onSkip = { viewModel.onEvent(PermissionSetupEvent.SkipTapped) },
        onBack = { viewModel.onEvent(PermissionSetupEvent.BackTapped) },
    )
}

/**
 * M6 — the trip-logged odometer moment, reached from the app-shell redirect
 * (docs/AUTO_ODOMETER_PLAN.md §4.4, wired in `:shared`'s `App.kt`).
 *
 * `ACCESS_BACKGROUND_LOCATION` is read here the same way every other permission in this
 * feature is — a composable controller at the route host, folded into the ViewModel via
 * [TripLoggedEvent.BackgroundLocationStatusObserved] — never bundled with the fine-location
 * ask (plan §5 step 5's "separate system dialog" rule).
 */
@Composable
internal fun AutoOdometerTripLoggedRoute(
    key: OdoDestination.AutoOdometer.TripLogged,
    navigationManager: NavigationManager,
) {
    val tripId = remember(key.tripId) { TripId(key.tripId) }
    val viewModel = koinViewModel<TripLoggedViewModel> { parametersOf(tripId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val backgroundLocation = rememberPermissionController(PlatformPermission.ACCESS_BACKGROUND_LOCATION)
    LaunchedEffect(backgroundLocation.status) {
        viewModel.onEvent(TripLoggedEvent.BackgroundLocationStatusObserved(backgroundLocation.status))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            TripLoggedEffect.NavigateBack -> navigationManager.back()
        }
    }

    TripLoggedScreen(
        state = state,
        onDone = { viewModel.onEvent(TripLoggedEvent.DoneTapped) },
        onReject = { viewModel.onEvent(TripLoggedEvent.RejectTapped) },
        onRejectConfirm = { viewModel.onEvent(TripLoggedEvent.RejectConfirmed) },
        onRejectDismiss = { viewModel.onEvent(TripLoggedEvent.RejectDismissed) },
        onUpgradeTapped = {
            viewModel.onEvent(TripLoggedEvent.UpgradeTapped)
            if (backgroundLocation.status == PermissionStatus.Blocked) {
                backgroundLocation.openAppSettings()
            } else {
                backgroundLocation.request()
            }
        },
        onUpgradeDismissed = { viewModel.onEvent(TripLoggedEvent.UpgradeDismissed) },
    )
}

/**
 * M7 — tracking toggle, trigger device, monthly stats and privacy controls.
 *
 * Every readiness permission this screen can show a fix-it row for is read here, at the
 * route host, exactly the way [AutoOdometerPermissionSetupRoute] reads its checklist steps —
 * a composable controller, not an injected port — and merged into one [TrackingReadiness]
 * snapshot on [SettingsEvent.ReadinessChanged] whenever any of them changes. That single
 * `LaunchedEffect` covers this screen's first composition (plan §5's "screen entry") and a
 * return from this screen's own settings deep-link; see [SettingsViewModel]'s KDoc for the
 * limitation that remains (a permission flipped in system Settings while this screen stays
 * open and foregrounded, without navigating away, is not caught until the next entry).
 */
@Composable
internal fun AutoOdometerSettingsRoute(navigationManager: NavigationManager) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val fineLocation = rememberPermissionController(PlatformPermission.ACCESS_FINE_LOCATION)
    val backgroundLocation = rememberPermissionController(PlatformPermission.ACCESS_BACKGROUND_LOCATION)
    val activityRecognition = rememberPermissionController(PlatformPermission.ACTIVITY_RECOGNITION)
    val bluetoothConnect = rememberPermissionController(PlatformPermission.BLUETOOTH_CONNECT)
    // The radio's own switch. Its *state* is not read here — the ViewModel collects that flow
    // directly — so this only exists to act on the BLUETOOTH_OFF row.
    val bluetoothEnabler = rememberBluetoothEnabler()

    LaunchedEffect(fineLocation.status, backgroundLocation.status, activityRecognition.status, bluetoothConnect.status) {
        viewModel.onEvent(
            SettingsEvent.ReadinessChanged(
                TrackingReadiness(
                    fineLocation = fineLocation.status == PermissionStatus.Granted,
                    backgroundLocation = backgroundLocation.status == PermissionStatus.Granted,
                    activityRecognition = activityRecognition.status == PermissionStatus.Granted,
                    bluetoothConnect = bluetoothConnect.status == PermissionStatus.Granted,
                    // Not one of the plan §5 fix-it rows (`TrackingReadiness.canTrack` never
                    // reads it either) — this screen does not mount a notifications controller.
                    notifications = true,
                ),
            ),
        )
    }

    /** Null for [ReadinessIssue.BLUETOOTH_OFF] — a radio switch is not a permission to ask for. */
    fun controllerFor(issue: ReadinessIssue) = when (issue) {
        ReadinessIssue.FINE_LOCATION -> fineLocation
        ReadinessIssue.BACKGROUND_LOCATION -> backgroundLocation
        ReadinessIssue.ACTIVITY_RECOGNITION -> activityRecognition
        ReadinessIssue.BLUETOOTH_CONNECT -> bluetoothConnect
        ReadinessIssue.BLUETOOTH_OFF -> null
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SettingsEffect.NavigateBack -> navigationManager.back()
            SettingsEffect.NavigateToDevicePicker -> navigationManager.navigateTo(OdoDestination.AutoOdometer.DevicePicker)
        }
    }

    SettingsScreen(
        state = state,
        onToggle = { viewModel.onEvent(SettingsEvent.ToggleTapped) },
        onResume = { viewModel.onEvent(SettingsEvent.ResumeTapped) },
        onChangeDevice = { viewModel.onEvent(SettingsEvent.ChangeDeviceTapped) },
        onPauseWeek = { viewModel.onEvent(SettingsEvent.PauseWeekTapped) },
        onDelete = { viewModel.onEvent(SettingsEvent.DeleteTapped) },
        onDeleteConfirm = { viewModel.onEvent(SettingsEvent.DeleteConfirmed) },
        onDeleteDismiss = { viewModel.onEvent(SettingsEvent.DeleteDismissed) },
        onFixIt = { issue ->
            val controller = controllerFor(issue)
            when {
                controller == null -> bluetoothEnabler.request()
                controller.status == PermissionStatus.Blocked -> controller.openAppSettings()
                else -> controller.request()
            }
        },
        onBack = { viewModel.onEvent(SettingsEvent.BackTapped) },
    )
}
