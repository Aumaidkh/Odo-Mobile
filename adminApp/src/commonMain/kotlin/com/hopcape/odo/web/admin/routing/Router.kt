package com.hopcape.odo.web.admin.routing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What a screen is allowed to know about navigation.
 *
 * A screen says where it wants to go; it never touches the address bar, the
 * history stack or the base path, all of which are browser facts that only
 * [com.hopcape.odo.web.admin.routing.BrowserRouter] should hold. That is also what
 * lets a test drive a screen at all — see [InMemoryRouter].
 */
@Stable
interface Router {

    /** The route on screen. Compose state: reading it in a composable subscribes. */
    val current: AdminRoute

    /** Navigate, leaving the current route in history for the back button. */
    fun go(route: AdminRoute)

    /**
     * Navigate without adding a history entry.
     *
     * For the cases where going back makes no sense: landing somewhere after a
     * sign-in, and being sent off the sign-in page once there is a session.
     */
    fun replace(route: AdminRoute)
}

/**
 * Where the app is mounted, taken from the path it was loaded at.
 *
 * In production the bundle is served under `/admin`, so every link has to carry
 * that prefix. Reading it from the live pathname rather than hardcoding it is what
 * lets the dev server serve from the root without breaking every link.
 */
fun basePathOf(pathname: String): String =
    if (pathname == ADMIN_BASE || pathname.startsWith("$ADMIN_BASE/")) ADMIN_BASE else ""

/**
 * The path this app used to be served from.
 *
 * It has its own origin now — `admin.odoapp.in`, at the root — so the base is
 * normally empty. Kept because the hosting config still 301s the old `/admin`
 * paths to the root for links saved while it lived under the marketing site, and
 * because
 * [basePathOf] has to keep working for anybody serving it under a path locally.
 */
const val ADMIN_BASE: String = "/admin"

/**
 * A router with no browser behind it.
 *
 * The screens are written against [Router], so this is what lets a test drive one.
 * It keeps no history: nothing off-browser has a back button to honour.
 */
class InMemoryRouter(initial: AdminRoute = AdminRoute.SignIn) : Router {
    override var current: AdminRoute by mutableStateOf(initial)
        private set

    override fun go(route: AdminRoute) {
        current = route
    }

    override fun replace(route: AdminRoute) {
        current = route
    }
}
