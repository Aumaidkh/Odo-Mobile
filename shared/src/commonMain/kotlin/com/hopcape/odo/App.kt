package com.hopcape.odo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopcape.odo.core.designsystem.component.OdoBanner
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.appstatus.AppAvailability
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.OdoNavHost
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.navigation.rememberNavigator
import com.hopcape.odo.feature.autoodometer.PendingTripLoggedProvider
import com.hopcape.odo.feature.dashboard.presentation.shell.OdoAppScaffold
import com.hopcape.odo.shared.resources.Res
import com.hopcape.odo.shared.resources.as_maintenance_banner_default
import com.hopcape.odo.units.DomainDistanceFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
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

    // The app-status gate (docs/APP_STATUS_PLAN.md). Starts at Allowed (fail open), so
    // nothing here changes until a refresh actually reports otherwise.
    val appStatusProvider = koinInject<AppStatusProvider>()
    val availability by appStatusProvider.availability.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    OdoTheme(darkTheme = settings.theme.isDark(), largerText = settings.largerText) {
        CompositionLocalProvider(LocalOdoDistanceFormat provides distanceFormat) {
            // Above the nav host, inside the theme: no route, deep link, or pending
            // redirect can navigate past a block, because there is no destination to
            // reach — and the block screen is still branded and honours dark/light.
            val current = availability
            if (shouldBlock(current)) {
                AppBlockedScreen(
                    blocked = current as AppAvailability.Blocked,
                    onRetry = { coroutineScope.launch { appStatusProvider.refresh() } },
                )
            } else {
                OdoAppContent(
                    koin = koin,
                    maintenanceMessage = (current as? AppAvailability.DegradedByMaintenance)?.message,
                )
            }
        }
    }
}

/**
 * Where the app opens: a returning owner goes straight to [OdoDestination.Home], a new one
 * to the [OdoDestination.Welcome] intro.
 *
 * [maintenanceMessage] non-null means a [AppAvailability.DegradedByMaintenance] window is
 * open — network work (sync, remote calls) stands down elsewhere, and this only shows the
 * banner; the local app keeps working underneath it.
 */
@Composable
private fun OdoAppContent(koin: Koin, maintenanceMessage: String? = null) {
    // Read once, not observed. A Flow would re-fire mid-session — the first sync that
    // touches the profile would re-evaluate the gate and could yank someone out of what
    // they were doing back to Welcome. Where the app *opened* is a question with one
    // answer per launch.
    //
    // Saved rather than merely remembered, because a configuration change (the OS
    // dark/light switch, rotation) rebuilds the activity and would otherwise send the app
    // back through StartupScreen while the database is read again. The back stack survives
    // that now, so a blank frame in front of it would be the only thing still moving.
    var onboarded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(koin) {
        if (onboarded != null) return@LaunchedEffect
        onboarded = withContext(Dispatchers.Default) {
            // Resolving the repository is what opens the database — and on first launch
            // seeds the vehicle catalog — so it happens off the main thread.
            val profiles = koin.get<OwnerProfileRepository>()
            profiles.observe().first()?.hasCompletedOnboarding == true
        }
    }

    // Column + weighted Box regardless of whether the banner shows, so the tree shape
    // never changes when maintenanceMessage flips — only its content does.
    Column(modifier = Modifier.fillMaxSize()) {
        if (maintenanceMessage != null) {
            OdoBanner(maintenanceMessage.ifBlank { stringResource(Res.string.as_maintenance_banner_default) })
        }
        Box(modifier = Modifier.weight(1f)) {
            // Nothing is rendered until the answer is in. The start destination is the
            // stack's first element, so guessing Welcome and correcting later would flash
            // the intro at every returning owner before jumping to Home.
            when (val returning = onboarded) {
                null -> StartupScreen()
                else -> OdoApp(
                    startDestination = if (returning) OdoDestination.Home else OdoDestination.Welcome,
                )
            }
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
    val currentDestination = navigator.backStack.lastOrNull()

    // D4's redirect: surface the trip-logged screen (M6) on the next top-level tab the
    // owner lands on, rather than a push notification. `PendingTripLoggedProvider` is
    // auto-odometer's one cross-feature contract (plan §4.4) — this is the only place
    // `:shared` reaches into a feature's Koin graph directly, because the redirect is
    // app-shell plumbing with no other natural home (`OdoAppScaffold` is chrome, not a
    // navigation decision). `shouldRedirectToTripLogged` carries the actual guard so it
    // stays unit-testable outside this composable.
    val pendingTripLogged = koinInject<PendingTripLoggedProvider>()
    val pendingTripId by produceState<TripId?>(initialValue = null, pendingTripLogged) {
        pendingTripLogged.pending().collect { value = it }
    }
    LaunchedEffect(currentDestination, pendingTripId) {
        val tripId = pendingTripId
        if (shouldRedirectToTripLogged(currentDestination, tripId)) {
            navigationManager.navigateTo(OdoDestination.AutoOdometer.TripLogged(tripId!!.value))
        }
    }

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
