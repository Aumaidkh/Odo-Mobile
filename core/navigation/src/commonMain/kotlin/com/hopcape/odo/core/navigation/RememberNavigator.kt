package com.hopcape.odo.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Create (and remember across recompositions) a [Navigator] rooted at
 * [startDestination]. Called once in the host (`:app`) and handed to [OdoNavHost];
 * features never see the result — they navigate through [NavigationManager].
 *
 * ```
 * val navigator = rememberNavigator(OdoDestination.Home)
 * OdoNavHost(navigator, navigationManager, entryProviders)
 * ```
 *
 * The back stack is held in `remember`, which survives recomposition. To also
 * survive configuration changes and process death, switch the underlying
 * [NavBackStack] to Nav3's `rememberNavBackStack(configuration, …)` once the
 * app's routes are `@Serializable` and registered in a `SavedStateConfiguration`.
 */
@Composable
fun rememberNavigator(startDestination: NavKey): Navigator {
    val backStack = remember { NavBackStack(startDestination) }
    return remember(backStack) { OdoNavigator(backStack) }
}
