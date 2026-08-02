package com.hopcape.odo.feature.profile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.notification.SystemNotificationSettings
import com.hopcape.odo.feature.profile.presentation.EditProfileEffect
import com.hopcape.odo.feature.profile.presentation.EditProfileScreen
import com.hopcape.odo.feature.profile.presentation.EditProfileViewModel
import com.hopcape.odo.feature.profile.presentation.NotificationsScreen
import com.hopcape.odo.feature.profile.presentation.NotificationsViewModel
import com.hopcape.odo.feature.profile.presentation.ProfileEvent
import com.hopcape.odo.feature.profile.presentation.ProfileScreen
import com.hopcape.odo.feature.profile.presentation.ProfileTelemetry
import com.hopcape.odo.feature.profile.presentation.ProfileViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.ExportDataSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.SignOutSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsCurrencySheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Profile's contribution to the navigation graph — the [OdoDestination.Profile] group:
 * the account home, its full-screen editors (edit / notifications), and its bottom-sheet
 * destinations (units / appearance / export / sign-out). "Go Pro" / "Manage plan" and the
 * export both reuse the shared [OdoDestination.Paywall] route, and "Privacy & permissions"
 * reuses support's own key — profile never imports another feature.
 */
internal class ProfileFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {

    private val nm get() = navigationManager

    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Profile.Root> { ProfileRoute(nm) }
        entry<OdoDestination.Profile.Edit> { EditProfileRoute(nm) }
        entry<OdoDestination.Profile.Notifications> { NotificationsRoute(nm) }

        val sheet = ModalBottomSheetSceneStrategy.bottomSheet()
        entry<OdoDestination.Profile.Units>(metadata = sheet) { UnitsRoute(nm) }
        entry<OdoDestination.Profile.Appearance>(metadata = sheet) { AppearanceRoute(nm) }
        entry<OdoDestination.Profile.Export>(metadata = sheet) { ExportRoute(nm) }
        entry<OdoDestination.Profile.SignOut>(metadata = sheet) { SignOutRoute(nm) }
    }
}

@Composable
internal fun ProfileRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<ProfileViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Opening a settings row is counted by the destination's own ViewModel, not here: the
    // screen or sheet is what an owner actually reached, and counting the tap as well would
    // put two events on the dashboard for one action.
    ProfileScreen(
        state = state,
        onBack = { navigationManager.back() },
        onEdit = { navigationManager.navigateTo(OdoDestination.Profile.Edit) },
        onGoPro = { navigationManager.navigateTo(OdoDestination.Paywall()) },
        onNotifications = { navigationManager.navigateTo(OdoDestination.Profile.Notifications) },
        onUnits = { navigationManager.navigateTo(OdoDestination.Profile.Units) },
        onAppearance = { navigationManager.navigateTo(OdoDestination.Profile.Appearance) },
        onExport = { navigationManager.navigateTo(OdoDestination.Profile.Export) },
        onPrivacy = { navigationManager.navigateTo(OdoDestination.Support.Privacy) },
        onHelp = { navigationManager.navigateTo(OdoDestination.Support.Help) },
        onSignIn = {
            viewModel.onEvent(ProfileEvent.SignInStarted)
            navigationManager.navigateTo(OdoDestination.Auth.Phone(next = OdoDestination.Profile.Root))
        },
        onSignOut = { navigationManager.navigateTo(OdoDestination.Profile.SignOut) },
    )
}

@Composable
private fun EditProfileRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<EditProfileViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            EditProfileEffect.Saved -> navigationManager.back()
            // Nothing of the owner's is left, so the app is back to first run. The whole
            // in-app stack goes with it: every screen behind this one is about data that
            // no longer exists.
            EditProfileEffect.DataDeleted -> navigationManager.navigateTo(
                OdoDestination.Welcome,
                popUpTo = OdoDestination.Home,
                inclusive = true,
            )
        }
    }

    EditProfileScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = { navigationManager.back() },
    )
}

@Composable
private fun NotificationsRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<NotificationsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val systemSettings = koinInject<SystemNotificationSettings>()

    NotificationsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        systemNotificationsEnabled = systemSettings.areEnabled(),
        onBack = { navigationManager.back() },
        onDeviceSettings = { systemSettings.open() },
    )
}

@Composable
private fun UnitsRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<UnitsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    UnitsCurrencySheetContent(
        state = state,
        onEvent = viewModel::onEvent,
        onDone = { navigationManager.back() },
    )
}

@Composable
private fun AppearanceRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<AppearanceViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppearanceSheetContent(
        state = state,
        onEvent = viewModel::onEvent,
        onDone = { navigationManager.back() },
    )
}

/**
 * The export sheet has no state of its own: it says what an export would contain and sends
 * the owner to the paywall, because the export itself is the paid Resale Passport (Phase
 * 2B). The telemetry facade is injected directly here for the same reason — there is no
 * ViewModel to hang one tap on.
 */
@Composable
private fun ExportRoute(navigationManager: NavigationManager) {
    val telemetry = koinInject<ProfileTelemetry>()
    ExportDataSheetContent(
        onUpgrade = { target ->
            telemetry.exportRequested(target)
            navigationManager.navigateTo(OdoDestination.Paywall(trigger = PAYWALL_TRIGGER_EXPORT))
        },
    )
}

@Composable
private fun SignOutRoute(navigationManager: NavigationManager) {
    val telemetry = koinInject<ProfileTelemetry>()
    SignOutSheetContent(
        // Confirming sign-out returns to the first-run value-prop, clearing the whole
        // in-app stack (popUpTo Home inclusive) so back can't re-enter the app.
        onSignOut = {
            telemetry.signedOut()
            navigationManager.navigateTo(
                OdoDestination.Welcome,
                popUpTo = OdoDestination.Home,
                inclusive = true,
            )
        },
        onCancel = { navigationManager.back() },
    )
}

/** What the paywall is told it was opened for, so the screen can name the reason. */
private const val PAYWALL_TRIGGER_EXPORT = "EXPORT"
