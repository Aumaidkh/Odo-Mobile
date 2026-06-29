package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * App-wide navigation surface, layered over a Navigation 3 user-owned back stack.
 *
 * Features depend on this interface — never on each other and never on the host's
 * [androidx.navigation3.ui.NavDisplay] — to move between destinations. Each
 * feature contributes its own [NavKey] route types; cross-feature jumps go through
 * the shared keys exposed here, which keeps the `:feature:*` modules decoupled
 * (the architecture's golden rule).
 *
 * Obtain one with [rememberNavigator] and render it with [OdoNavHost].
 */
interface Navigator {

    /**
     * The live Navigation 3 back stack. The top element ([List.last]) is the
     * destination currently on screen. This is the same observable list
     * [OdoNavHost] renders, so the typed operations below reflect immediately in
     * the UI. Read it to inspect the stack; mutate it only through those operations.
     */
    val backStack: List<NavKey>

    /** `true` when there is a destination to pop back to (more than just the root). */
    val canGoBack: Boolean

    /** Push [destination] onto the back stack and show it. */
    fun navigate(destination: NavKey)

    /** Pop the current destination. No-op when already at the root. */
    fun goBack()

    /**
     * Pop destinations until [destination] is reached. With [inclusive] = `false`
     * (default) [destination] is left on top; with `true` it is popped too.
     * Returns `false` (and changes nothing) if [destination] is not in the stack.
     */
    fun popUpTo(destination: NavKey, inclusive: Boolean = false): Boolean
}
