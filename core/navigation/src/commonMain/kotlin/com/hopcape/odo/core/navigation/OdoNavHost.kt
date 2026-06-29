package com.hopcape.odo.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

/**
 * The app's navigation host — a thin wrapper over Navigation 3's [NavDisplay].
 *
 * This is the command-bus overload (the inspiration's `AppNavHost`): it collects
 * [navigationManager]'s commands and applies them to [navigator], and builds the
 * graph by letting every [FeatureEntryProvider] register its screens. Everything
 * here is host-only — features just emit commands and contribute entries.
 *
 * Wire it once in `:app`:
 * ```
 * val navigator = rememberNavigator(OdoDestination.Home)
 * OdoNavHost(navigator, navigationManager, entryProviders = getKoin().getAll())
 * ```
 */
@Composable
fun OdoNavHost(
    navigator: Navigator,
    navigationManager: NavigationManager,
    entryProviders: List<FeatureEntryProvider>,
    modifier: Modifier = Modifier,
) {
    // Drain the command bus into back-stack operations. Restarts if either the
    // navigator or the manager identity changes.
    LaunchedEffect(navigator, navigationManager) {
        navigationManager.commands.collect { command -> navigator.execute(command) }
    }

    OdoNavHost(navigator = navigator, modifier = modifier) {
        entryProviders.forEach { provider -> with(provider) { registerEntries() } }
    }
}

/**
 * Lower-level host overload for when you want to register entries inline (or have
 * no command bus yet). Renders [navigator]'s back stack and wires system/gesture
 * back to [Navigator.goBack].
 *
 * ```
 * OdoNavHost(navigator) {
 *     entry<OdoDestination.Home> { HomeScreen() }
 *     entry<OdoDestination.CarDetail> { key -> CarDetailScreen(key.carId) }
 * }
 * ```
 */
@Composable
fun OdoNavHost(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    builder: EntryProviderScope<NavKey>.() -> Unit,
) {
    NavDisplay(
        backStack = navigator.backStack,
        onBack = { navigator.goBack() },
        modifier = modifier,
        entryProvider = entryProvider(builder = builder),
    )
}
