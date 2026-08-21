package com.hopcape.odo.web.blog.routing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What a screen is allowed to know about navigation.
 *
 * Deliberately three members. A screen says where it wants to go; it never
 * touches the address bar, the history stack or the base path, all of which are
 * browser facts that only [com.hopcape.odo.web.blog.routing.BrowserRouter]
 * should hold. That is also what makes a preview or a test able to run a screen
 * at all — see [InMemoryRouter].
 */
@Stable
interface Router {

    /** The route on screen. Compose state: reading it in a composable subscribes. */
    val current: BlogRoute

    /** Navigate, leaving the current route in history for the back button. */
    fun go(route: BlogRoute)

    /**
     * Navigate without adding a history entry.
     *
     * For the cases where going back to where you were makes no sense: typing in
     * the search box (every keystroke would otherwise be a back step) and
     * correcting the URL after a post is saved and finally has a slug.
     */
    fun replace(route: BlogRoute)
}

/**
 * Where the app is mounted, taken from the path it was loaded at.
 *
 * In production the bundle is served under `/blog`, so every link has to carry
 * that prefix. The Gradle dev server serves it at the root instead, and
 * hardcoding `/blog` would break every link there. Reading it from the live
 * pathname makes both work without a build flag.
 */
fun basePathOf(pathname: String): String =
    if (pathname == BLOG_BASE || pathname.startsWith("$BLOG_BASE/")) BLOG_BASE else ""

/** The path `landing/firebase.json` serves this app from. */
const val BLOG_BASE: String = "/blog"

/**
 * A router with no browser behind it.
 *
 * The screens are written against [Router], so this is what lets a `@Preview`
 * render one and a test drive one. It keeps no history: nothing off-browser has
 * a back button to honour.
 */
class InMemoryRouter(initial: BlogRoute = BlogRoute.Public.Index) : Router {
    override var current: BlogRoute by mutableStateOf(initial)
        private set

    override fun go(route: BlogRoute) {
        current = route
    }

    override fun replace(route: BlogRoute) {
        current = route
    }
}
