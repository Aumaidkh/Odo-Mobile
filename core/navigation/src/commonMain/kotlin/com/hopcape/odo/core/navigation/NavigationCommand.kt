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
        /**
         * Skip the push when [destination] is already on top.
         *
         * Note this is *not* what stops duplicates — a key already deeper in the stack is
         * brought forward whatever this says, because Nav3 cannot hold the same key twice.
         * This only decides whether re-selecting the current screen is a no-op.
         */
        val singleTop: Boolean = true,
    ) : NavigationCommand

    /**
     * Leave a finished sub-flow and go to [destination].
     *
     * Every entry at the top of the stack that [belongsToFlow] accepts is popped first, so
     * the screens the owner walked through on the way here are gone instead of waiting under
     * what comes next. [NavigateTo]'s [NavigateTo.popUpTo] cannot do this: it needs a screen
     * that is still on the stack, and a flow whose steps replace one another no longer has
     * one. The push is skipped when [destination] is already on top, so a flow that started
     * where it ends lands back on that entry rather than on a second copy of it.
     */
    data class FinishFlow(
        val destination: OdoDestination,
        val belongsToFlow: (OdoDestination) -> Boolean,
    ) : NavigationCommand

    /**
     * Leave a finished sub-flow, landing on whatever opened it.
     *
     * The same popping as [FinishFlow] with nothing pushed afterwards — for a flow that ends
     * by handing the owner back where they came from rather than somewhere of its own. What
     * that place is depends on where the flow was started, which is exactly what the stack
     * already knows and the flow does not.
     */
    data class LeaveFlow(val belongsToFlow: (OdoDestination) -> Boolean) : NavigationCommand

    /** Pop the current destination. */
    data object Back : NavigationCommand
}
