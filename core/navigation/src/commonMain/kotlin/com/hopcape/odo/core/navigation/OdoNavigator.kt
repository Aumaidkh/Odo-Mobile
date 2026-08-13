package com.hopcape.odo.core.navigation

import androidx.navigation3.runtime.NavBackStack

/**
 * Default [Navigator] backed directly by a Navigation 3 [NavBackStack] (an
 * observable [MutableList] of [OdoDestination]).
 *
 * Every operation mutates the back stack in place, so the host's `NavDisplay`
 * recomposes automatically. Kept `internal` — callers create one via
 * [rememberNavigator] and only ever see the [Navigator] interface.
 */
internal class OdoNavigator(
    private val navBackStack: NavBackStack<OdoDestination>,
) : Navigator {

    override val backStack: List<OdoDestination>
        get() = navBackStack

    override val canGoBack: Boolean
        get() = navBackStack.size > 1

    override fun navigate(destination: OdoDestination) {
        navBackStack.add(destination)
    }

    override fun goBack() {
        if (canGoBack) navBackStack.removeAt(navBackStack.lastIndex)
    }

    override fun popUpTo(destination: OdoDestination, inclusive: Boolean): Boolean {
        val index = navBackStack.lastIndexOf(destination)
        if (index < 0) return false
        val target = if (inclusive) index else index + 1
        while (navBackStack.size > target) {
            navBackStack.removeAt(navBackStack.lastIndex)
        }
        return true
    }
}
