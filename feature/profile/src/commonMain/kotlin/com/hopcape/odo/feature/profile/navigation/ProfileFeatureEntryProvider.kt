package com.hopcape.odo.feature.profile.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.profile.presentation.EditProfileScreen
import com.hopcape.odo.feature.profile.presentation.NotificationsScreen
import com.hopcape.odo.feature.profile.presentation.ProfileScreen
import com.hopcape.odo.feature.profile.presentation.sampleProProfile
import com.hopcape.odo.feature.profile.presentation.sheets.AppearanceSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.ExportDataSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.SignOutSheetContent
import com.hopcape.odo.feature.profile.presentation.sheets.UnitsCurrencySheetContent

/**
 * Profile's contribution to the navigation graph — the [OdoDestination.Profile] group:
 * the account home, its full-screen editors (edit / notifications), and its bottom-sheet
 * destinations (units / appearance / export / sign-out). "Go Pro" / "Manage plan" reuse
 * the shared [OdoDestination.Paywall] route. Each sheet is pushed over the home and pops
 * straight back on dismiss.
 */
internal class ProfileFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {

    private val nm get() = navigationManager

    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Profile.Root> { ProfileRoute(nm) }

        entry<OdoDestination.Profile.Edit> {
            EditProfileScreen(onClose = { nm.back() }, onSave = { nm.back() }, onDelete = { /* TODO: delete-account flow. */ })
        }
        entry<OdoDestination.Profile.Notifications> {
            NotificationsScreen(onBack = { nm.back() }, onDeviceSettings = { /* TODO: open device settings. */ })
        }

        val sheet = ModalBottomSheetSceneStrategy.bottomSheet()
        entry<OdoDestination.Profile.Units>(metadata = sheet) {
            UnitsCurrencySheetContent(onDone = { nm.back() }, onOpenCurrency = { /* TODO: currency picker. */ })
        }
        entry<OdoDestination.Profile.Appearance>(metadata = sheet) {
            AppearanceSheetContent(onDone = { nm.back() })
        }
        entry<OdoDestination.Profile.Export>(metadata = sheet) {
            ExportDataSheetContent(onDownloadPdf = { nm.back() }, onRequestExport = { nm.back() })
        }
        entry<OdoDestination.Profile.SignOut>(metadata = sheet) {
            SignOutSheetContent(
                // Confirming sign-out returns to the first-run value-prop, clearing the
                // whole in-app stack (popUpTo Home inclusive) so back can't re-enter the app.
                onSignOut = { nm.navigateTo(OdoDestination.Welcome, popUpTo = OdoDestination.Home, inclusive = true) },
                onCancel = { nm.back() },
            )
        }
    }
}

/**
 * The profile home route host. Renders sample state until the account use case lands;
 * swap `sampleProProfile()` ↔ `sampleFreeProfile()` to preview the Pro vs free card.
 */
@Composable
internal fun ProfileRoute(navigationManager: NavigationManager) {
    ProfileScreen(
        state = sampleProProfile(),
        onBack = { navigationManager.back() },
        onEdit = { navigationManager.navigateTo(OdoDestination.Profile.Edit) },
        onGoPro = { navigationManager.navigateTo(OdoDestination.Paywall()) },
        onRestore = { /* TODO: restore purchases. */ },
        onNotifications = { navigationManager.navigateTo(OdoDestination.Profile.Notifications) },
        onUnits = { navigationManager.navigateTo(OdoDestination.Profile.Units) },
        onAppearance = { navigationManager.navigateTo(OdoDestination.Profile.Appearance) },
        onExport = { navigationManager.navigateTo(OdoDestination.Profile.Export) },
        onPrivacy = { /* TODO: privacy & permissions screen. */ },
        onHelp = { /* TODO: help & support. */ },
        onSignOut = { navigationManager.navigateTo(OdoDestination.Profile.SignOut) },
    )
}
