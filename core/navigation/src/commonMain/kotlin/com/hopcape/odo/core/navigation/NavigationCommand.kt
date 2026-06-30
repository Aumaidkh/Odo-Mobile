package com.hopcape.odo.core.navigation

/**
 * A navigation intent emitted by a feature (usually from a ViewModel) and carried
 * over the [NavigationManager] bus to the host, which translates it into back-stack
 * operations. Features describe *what* should happen; only the host knows *how*.
 */
sealed interface NavigationCommand {

    /**
     * Go to [destination]. Optionally [popUpTo] an existing destination first
     * (popping it too when [inclusive]); [singleTop] skips the push when
     * [destination] is already on top — the standard bottom-tab reselection guard.
     */
    data class NavigateTo(
        val destination: OdoDestination,
        val popUpTo: OdoDestination? = null,
        val inclusive: Boolean = false,
        val singleTop: Boolean = true,
    ) : NavigationCommand

    /** Pop the current destination. */
    data object Back : NavigationCommand
}
