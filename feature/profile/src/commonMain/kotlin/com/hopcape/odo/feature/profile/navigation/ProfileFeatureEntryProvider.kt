package com.hopcape.odo.feature.profile.navigation

import com.hopcape.odo.core.common.BuildInfo
import com.hopcape.odo.feature.profile.presentation.ConfigOverridesScreen
import com.hopcape.odo.feature.profile.presentation.ConfigOverridesViewModel
import com.hopcape.odo.feature.profile.presentation.DeveloperOptionsScreen
import com.hopcape.odo.feature.profile.presentation.logs.LogsScreen
import com.hopcape.odo.feature.profile.presentation.logs.LogsViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.config.FeatureConfig
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
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
import com.hopcape.odo.feature.profile.presentation.privacy.DeleteAccountEffect
import com.hopcape.odo.feature.profile.presentation.privacy.DeleteAccountScreen
import com.hopcape.odo.feature.profile.presentation.privacy.DeleteAccountViewModel
import com.hopcape.odo.feature.profile.presentation.privacy.PrivacyScreen
import com.hopcape.odo.feature.profile.presentation.privacy.PrivacyViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.ExportDataSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.SignOutEffect
import com.hopcape.odo.feature.profile.presentation.sheets.SignOutSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.SignOutViewModel
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsCurrencySheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Profile's contribution to the navigation graph — the [OdoDestination.Profile] group:
 * the account home, its full-screen editors (edit / notifications), and its bottom-sheet
 * destinations (units / appearance / export / sign-out). "Go Pro" / "Manage plan" and the
 * export both reuse the shared [OdoDestination.Paywall.Plans] route, and "Privacy & permissions"
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
        entry<OdoDestination.Profile.Privacy> { PrivacyRoute(nm) }
        entry<OdoDestination.Profile.DeleteAccount> { DeleteAccountRoute(nm) }
        // Registered in every build; the row that opens it is behind BuildInfo.isDebug.
        entry<OdoDestination.Profile.DeveloperOptions> { DeveloperOptionsRoute(nm) }
        entry<OdoDestination.Profile.ConfigOverrides> { ConfigOverridesRoute(nm) }
        entry<OdoDestination.Profile.Logs> { LogsRoute(nm) }

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
    val uriHandler = LocalUriHandler.current
    ProfileScreen(
        state = state,
        onBack = { navigationManager.back() },
        onEdit = { navigationManager.navigateTo(OdoDestination.Profile.Edit) },
        onGoPro = { navigationManager.navigateTo(OdoDestination.Paywall.Plans()) },
        // The store's own subscription page. Cancelling, changing plan and fixing a card all
        // happen there — Play requires it, and an in-app cancel that did not cancel would be
        // the worst version of this screen.
        onManagePlan = { url -> uriHandler.openUri(url) },
        onNotifications = { navigationManager.navigateTo(OdoDestination.Profile.Notifications) },
        onUnits = { navigationManager.navigateTo(OdoDestination.Profile.Units) },
        onAppearance = { navigationManager.navigateTo(OdoDestination.Profile.Appearance) },
        // The questionnaire, asked for the goal alone. It is the same destination first-run
        // setup could use; naming the keys is what keeps editing one answer from reopening
        // the whole flow.
        onGoals = { navigationManager.navigateTo(OdoDestination.Questionnaire(listOf(QuestionKeys.Goal.value))) },
        onExport = { navigationManager.navigateTo(OdoDestination.Profile.Export) },
        onPrivacy = { navigationManager.navigateTo(OdoDestination.Profile.Privacy) },
        debugToolsVisible = BuildInfo.isDebug,
        onDeveloperOptions = { navigationManager.navigateTo(OdoDestination.Profile.DeveloperOptions) },
        onShowAround = { viewModel.onEvent(ProfileEvent.ShowAroundTapped) },
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
    // Read here rather than held in the ViewModel's state: it is a build capability, not
    // something the screen's own events can change, and this is where the other one
    // (SystemNotificationSettings) is read too.
    val featureConfig = koinInject<FeatureConfig>()

    NotificationsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        systemNotificationsEnabled = systemSettings.areEnabled(),
        autoDetectOffered = featureConfig.refuelDetectEnabled,
        onBack = { navigationManager.back() },
        onDeviceSettings = { systemSettings.open() },
        onAutoDetect = { navigationManager.navigateTo(OdoDestination.Refuel.AutoDetect) },
    )
}

/**
 * Privacy & permissions.
 *
 * "Privacy policy" goes to support's own key rather than a screen of profile's — the policy
 * is a document, shared with the Help sheet, and profile never imports another feature.
 */
@Composable
private fun PrivacyRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<PrivacyViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    PrivacyScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { navigationManager.back() },
        onPrivacyPolicy = { navigationManager.navigateTo(OdoDestination.Support.Privacy) },
        onDeleteAccount = { navigationManager.navigateTo(OdoDestination.Profile.DeleteAccount) },
    )
}

/**
 * The account erase.
 *
 * Moves on the ViewModel's effect, never on the tap — the same rule the sign-out sheet
 * learned the hard way. Navigating first would tear the screen down mid-erase, and here that
 * would abandon an irreversible operation with nothing left to report its outcome to.
 */
@Composable
private fun DeleteAccountRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<DeleteAccountViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // Nothing of the owner's is left anywhere, so the app is back to first run. The
            // whole in-app stack goes with it: every screen behind this one is about data
            // that no longer exists, and back must not be able to re-enter it.
            DeleteAccountEffect.Deleted -> navigationManager.navigateTo(
                OdoDestination.Welcome,
                popUpTo = OdoDestination.Home,
                inclusive = true,
            )
        }
    }

    DeleteAccountScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onCancel = { navigationManager.back() },
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
    val activeCar = koinInject<ActiveCarProvider>()
    ExportDataSheetContent(
        onExport = {
            telemetry.exportRequested(ProfileTelemetry.ExportTarget.PDF)
            // The share sheet owns both the document and the Pro gate, so this only has to
            // say which car. With no car there is nothing to export and nowhere to go.
            activeCar.activeCarId.value?.let { carId ->
                // Close this sheet before opening that one. Both are bottom-sheet
                // destinations, and Nav3 has nothing left to overlay when a sheet is pushed
                // straight onto a sheet — it throws "Overlaid entries must not be empty".
                // Popping first also means back from the record returns to the profile
                // rather than to an export sheet the owner is already done with.
                navigationManager.back()
                navigationManager.navigateTo(OdoDestination.ServiceLog.Share(carId = carId.value))
            }
        },
    )
}

/**
 * The sign-out sheet moves on the ViewModel's effect, not on the tap. Signing out clears the
 * session and wipes the local copy, and navigating first would tear the sheet down before
 * either finished — which is exactly what left the app signed in with all its data after a
 * "sign-out".
 */
@Composable
private fun SignOutRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<SignOutViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // Nothing of the owner's is left, so the app is back to first run. The whole
            // in-app stack goes with it (popUpTo Home inclusive), so back can't re-enter.
            SignOutEffect.SignedOut -> navigationManager.navigateTo(
                OdoDestination.Welcome,
                popUpTo = OdoDestination.Home,
                inclusive = true,
            )
        }
    }

    SignOutSheetContent(
        state = state,
        onSignOut = viewModel::onConfirm,
        onCancel = { navigationManager.back() },
    )
}

/** What the paywall is told it was opened for, so the screen can name the reason. */

/**
 * The developer-tools hub. Its route is registered in every build and only a debug build
 * offers a way in (from the account home), the same shape the refuel routes use:
 * unreachable, not removed.
 */
@Composable
private fun DeveloperOptionsRoute(navigationManager: NavigationManager) {
    DeveloperOptionsScreen(
        onConfigOverrides = { navigationManager.navigateTo(OdoDestination.Profile.ConfigOverrides) },
        onLogs = { navigationManager.navigateTo(OdoDestination.Profile.Logs) },
        onBack = { navigationManager.back() },
    )
}

/**
 * The debug config screen. Reached from [DeveloperOptionsRoute], never directly from the
 * account home.
 */
@Composable
private fun ConfigOverridesRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<ConfigOverridesViewModel>()
    val keys by viewModel.keys.collectAsStateWithLifecycle()

    ConfigOverridesScreen(
        keys = keys,
        editable = viewModel.editable,
        onSet = viewModel::set,
        onClear = viewModel::clear,
        onClearAll = viewModel::clearAll,
        onBack = { navigationManager.back() },
    )
}

/**
 * The log viewer. Polling starts when this composable enters composition and stops when it
 * leaves — [DisposableEffect] rather than the ViewModel's `init {}`, since the ViewModel
 * outlives a configuration change but the screen shouldn't keep polling in the background
 * once it's off-screen for good (`onCleared` also stops it, for the process-death case).
 */
@Composable
private fun LogsRoute(navigationManager: NavigationManager) {
    val viewModel = koinViewModel<LogsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    LogsScreen(
        state = state,
        onSearchChanged = viewModel::onSearchChanged,
        onLevelToggled = viewModel::onLevelToggled,
        onTagToggled = viewModel::onTagToggled,
        onBack = { navigationManager.back() },
    )
}
