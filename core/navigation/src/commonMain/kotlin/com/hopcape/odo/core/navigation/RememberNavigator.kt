package com.hopcape.odo.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer

/**
 * Create (and remember) a [Navigator] rooted at [startDestination]. Called once in the host
 * (`:app`) and handed to [OdoNavHost]; features never see the result — they navigate through
 * [NavigationManager].
 *
 * ```
 * val navigator = rememberNavigator(OdoDestination.Home)
 * OdoNavHost(navigator, navigationManager, entryProviders)
 * ```
 *
 * The back stack is written to saved state, so it survives a configuration change (the OS
 * dark/light switch, rotation, a font-size or locale change) and process death. Losing it
 * would drop the owner back on the start destination with everything behind them gone.
 *
 * [OdoDestination] is a sealed hierarchy, which is what makes this cheap: closed polymorphism
 * covers every key, so there is no `SerializersModule` listing subtypes to keep in step with
 * the registry. A new destination is covered the moment it is declared, and one that forgets
 * `@Serializable` fails to compile rather than failing to restore.
 *
 * [startDestination] is only the stack's initial element. On a restore the saved stack wins,
 * so it is read once per launch and ignored thereafter.
 */
@Composable
fun rememberNavigator(startDestination: OdoDestination): Navigator {
    val backStack = rememberSerializable(
        serializer = NavBackStackSerializer(OdoDestination.serializer()),
    ) {
        NavBackStack(startDestination)
    }
    return remember(backStack) { OdoNavigator(backStack) }
}
