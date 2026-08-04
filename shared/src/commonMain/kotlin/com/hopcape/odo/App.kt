package com.hopcape.odo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.OdoNavHost
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.navigation.rememberNavigator
import com.hopcape.odo.feature.dashboard.presentation.shell.OdoAppScaffold
import com.hopcape.odo.units.DomainDistanceFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import org.koin.core.Koin
import org.koin.compose.koinInject

/**
 * The app's composition root. Koin is already started by the platform bootstrap
 * (Android `OdoApplication` / iOS `MainViewController`), and Koin 4.x exposes that
 * global graph to the composition automatically — so feature routes can
 * `koinViewModel()` / `koinInject()` without any wrapper here.
 *
 * It makes two decisions. **How the app looks and measures**: the owner's theme, text size
 * and distance unit are read here and provided to everything below, because they are one
 * setting each for the whole app rather than a field on a dozen screens' state. **Where it
 * opens**: a returning owner goes straight to [OdoDestination.Home], a new one to the
 * [OdoDestination.Welcome] intro — navigation wiring, not business logic, since the fact
 * behind it (`OwnerProfile.hasCompletedOnboarding`) is already owned by the domain.
 */
@Composable
fun App() {
    val koin = getKoin()

    // Observed, unlike the start destination: the appearance sheet changes these while the
    // app is running, and the whole app has to redraw behind it. Resolving the repository
    // opens the database, so that happens off the main thread like the read below.
    val settings by produceState(AppSettings.Default, koin) {
        withContext(Dispatchers.Default) {
            koin.get<AppSettingsRepository>().observe().collect { value = it }
        }
    }

    // The unit every distance on every screen is shown and typed in. Provided here rather
    // than threaded through each feature's state: it is one setting the whole app reads,
    // and the alternative is the same field on a dozen unrelated UI states.
    val distanceFormat = remember(settings.distanceUnit) { DomainDistanceFormat(settings.distanceUnit) }

    OdoTheme(darkTheme = settings.theme.isDark(), largerText = settings.largerText) {
        CompositionLocalProvider(LocalOdoDistanceFormat provides distanceFormat) {
            OdoAppContent(koin)
        }
    }
}

/**
 * Where the app opens: a returning owner goes straight to [OdoDestination.Home], a new one
 * to the [OdoDestination.Welcome] intro.
 */
@Composable
private fun OdoAppContent(koin: Koin) {
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
        onScan = { navigationManager.navigateTo(OdoDestination.BillScanner.Capture()) },
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

/**
 * Whether this preference means the dark palette. [ThemePreference.SYSTEM] defers to the
 * device, which is what an owner who has never opened the appearance sheet gets.
 */
@Composable
private fun ThemePreference.isDark(): Boolean = when (this) {
    ThemePreference.DARK -> true
    ThemePreference.LIGHT -> false
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
}
