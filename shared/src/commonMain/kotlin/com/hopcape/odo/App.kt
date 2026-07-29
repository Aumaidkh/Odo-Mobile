package com.hopcape.odo

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.OdoNavHost
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.navigation.rememberNavigator
import com.hopcape.odo.feature.dashboard.presentation.shell.OdoAppScaffold
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * The app's composition root. Koin is already started by the platform bootstrap
 * (Android `OdoApplication` / iOS `MainViewController`), and Koin 4.x exposes that
 * global graph to the composition automatically — so feature routes can
 * `koinViewModel()` / `koinInject()` without any wrapper here.
 *
 * The nav host is driven entirely from DI: the shared [NavigationManager] and every
 * feature's [FeatureEntryProvider] (`getAll` — the multibinding analog). First run
 * lands on the [OdoDestination.Welcome] intro carousel, which routes into
 * [OdoDestination.Onboarding] (car setup); completing that routes to [OdoDestination.Home],
 * which the dashboard feature contributes.
 *
 * The dashboard's [OdoAppScaffold] wraps the host with the bottom bar. The bar shows
 * only while a bottom-nav root is on screen, so a detail, the scan flow or a sheet gets
 * the full height.
 */
@Composable
fun App() {
    OdoTheme {
        val navigationManager = koinInject<NavigationManager>()
        val navigator = rememberNavigator(OdoDestination.Welcome)
        // The provider set is fixed once Koin is started, so resolve it once rather than
        // on every recomposition — App() recomposes on each tab switch.
        val koin = getKoin()
        val entryProviders = remember(koin) { koin.getAll<FeatureEntryProvider>() }

        // The live top of the stack drives both the selected tab and whether the bar shows.
        val currentDestination = navigator.backStack.lastOrNull() as? OdoDestination

        OdoAppScaffold(
            currentDestination = currentDestination,
            // Tab semantics: pop back to Home, then land on the tab. singleTop (the
            // navigateTo default) skips the re-push when that tab is already on top, so
            // reselecting the current tab is a no-op rather than a stack of duplicates.
            onSelectTab = { tab -> navigationManager.navigateTo(tab, popUpTo = OdoDestination.Home) },
            onScan = { navigationManager.navigateTo(OdoDestination.BillScanner.Capture) },
        ) { padding ->
            // Apply the shell's bar/inset padding once, then consume it: each screen's own
            // OdoScreen scaffold would otherwise add the status and nav insets a second
            // time, pushing every title down under a doubled top inset.
            OdoNavHost(
                navigator = navigator,
                navigationManager = navigationManager,
                entryProviders = entryProviders,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            )
        }
    }
}
