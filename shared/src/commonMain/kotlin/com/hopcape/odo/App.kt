package com.hopcape.odo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.OdoNavHost
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.navigation.rememberNavigator
import com.hopcape.odo.feature.dashboard.presentation.shell.OdoAppScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * The app's composition root. Koin is already started by the platform bootstrap
 * (Android `OdoApplication` / iOS `MainViewController`), and Koin 4.x exposes that
 * global graph to the composition automatically — so feature routes can
 * `koinViewModel()` / `koinInject()` without any wrapper here.
 *
 * Its one decision is where the app opens: a returning owner goes straight to
 * [OdoDestination.Home], a new one to the [OdoDestination.Welcome] intro. That is
 * navigation wiring, not business logic — the fact behind it
 * (`OwnerProfile.hasCompletedOnboarding`) is already owned by the domain, and this reads it
 * through the existing `OwnerProfileRepository` rather than through a port invented to
 * carry one boolean.
 */
@Composable
fun App() {
    OdoTheme {
        val koin = getKoin()

        // Read once, not observed. A Flow would re-fire mid-session — the first sync that
        // touches the profile would re-evaluate the gate and could yank someone out of what
        // they were doing back to Welcome. Where the app *opened* is a question with one
        // answer per launch.
        val startDestination by produceState<OdoDestination?>(initialValue = null, koin) {
            value = withContext(Dispatchers.Default) {
                // Resolving the repository is what opens the database — and on first launch
                // seeds the vehicle catalog — so it happens off the main thread.
                val profiles = koin.get<OwnerProfileRepository>()
                val onboarded = profiles.observe().first()?.hasCompletedOnboarding == true
                if (onboarded) OdoDestination.Home else OdoDestination.Welcome
            }
        }

        // Nothing is rendered until the answer is in. `rememberNavigator` captures its start
        // destination in a `remember`, so guessing Welcome and correcting later would flash
        // the intro at every returning owner before jumping to Home.
        when (val start = startDestination) {
            null -> StartupScreen()
            else -> OdoApp(startDestination = start)
        }
    }
}

/**
 * What fills the window between launch and knowing where to go — the themed background and
 * nothing else.
 *
 * Deliberately not a spinner: the wait is a single local-DB read, so a spinner would flash
 * and read as slowness the app doesn't have. An empty branded surface simply looks like the
 * app still launching, which is what is happening.
 */
@Composable
private fun StartupScreen() {
    Box(Modifier.fillMaxSize().background(OdoTheme.colors.bg))
}

/**
 * The app proper, once [startDestination] is known.
 *
 * The nav host is driven entirely from DI: the shared [NavigationManager] and every
 * feature's [FeatureEntryProvider] (`getAll` — the multibinding analog). First run lands on
 * the [OdoDestination.Welcome] intro carousel, which routes into [OdoDestination.Onboarding]
 * (car setup); completing that routes on by the owner's goal.
 *
 * The dashboard's [OdoAppScaffold] wraps the host with the bottom bar. The bar shows only
 * while a bottom-nav root is on screen, so a detail, the scan flow or a sheet gets the full
 * height.
 */
@Composable
private fun OdoApp(startDestination: OdoDestination) {
    val navigationManager = koinInject<NavigationManager>()
    val navigator = rememberNavigator(startDestination)
    // The provider set is fixed once Koin is started, so resolve it once rather than
    // on every recomposition — this recomposes on each tab switch.
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
