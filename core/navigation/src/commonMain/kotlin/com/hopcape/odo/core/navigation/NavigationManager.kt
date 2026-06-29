package com.hopcape.odo.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The feature-facing navigation surface — the single thing a ViewModel injects to
 * navigate. Features emit [NavigationCommand]s here; they never touch the
 * [Navigator], the back stack, or another feature. The host collects [commands]
 * and applies them (see [OdoNavHost]).
 *
 * Provided as a Koin `single` by [coreNavigationModule]; obtain a standalone
 * instance for non-DI call sites (or tests) via the [NavigationManager] factory.
 */
interface NavigationManager {
    /** Hot stream of navigation intents, replayed to the active host collector. */
    val commands: SharedFlow<NavigationCommand>

    /** Emit a navigation intent. Safe to call from any thread; never suspends. */
    fun navigate(command: NavigationCommand)
}

/** Create a default [NavigationManager]. The Koin `single` uses this too. */
fun NavigationManager(): NavigationManager = DefaultNavigationManager()

/**
 * Default [NavigationManager] backed by a buffered [MutableSharedFlow] so an emit
 * is never lost while the host is (re)subscribing, and never blocks the caller.
 */
internal class DefaultNavigationManager : NavigationManager {

    private val _commands = MutableSharedFlow<NavigationCommand>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val commands: SharedFlow<NavigationCommand> = _commands.asSharedFlow()

    override fun navigate(command: NavigationCommand) {
        _commands.tryEmit(command)
    }
}

// --- Ergonomic shorthands so call sites read intent-first ---

fun NavigationManager.navigateTo(
    destination: OdoDestination,
    popUpTo: OdoDestination? = null,
    inclusive: Boolean = false,
    singleTop: Boolean = true,
) = navigate(NavigationCommand.NavigateTo(destination, popUpTo, inclusive, singleTop))

fun NavigationManager.back() = navigate(NavigationCommand.Back)

/**
 * Ambient [NavigationManager] for Compose call sites (e.g. a screen reading it
 * directly in a demo). In production a ViewModel injects [NavigationManager] via
 * Koin instead of reaching for this local.
 */
val LocalNavigationManager = staticCompositionLocalOf<NavigationManager> {
    error("No NavigationManager provided. Provide one via CompositionLocalProvider.")
}
