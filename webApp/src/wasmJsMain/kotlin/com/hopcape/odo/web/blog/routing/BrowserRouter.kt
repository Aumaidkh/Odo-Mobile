package com.hopcape.odo.web.blog.routing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window

/**
 * The [Router] the deployed app runs on: the address bar is the state.
 *
 * The URL leads and the app follows, rather than the other way round. That is
 * not a style choice — the blog exists to be found, so every page has to be a
 * real address that survives a refresh, a share and a back button. Navigating
 * therefore does two things in one place: it pushes the address and it moves
 * the app, and `popstate` moves the app when the reader pushes the address.
 *
 * Refresh only survives because `landing/firebase.json` rewrites everything
 * under `/blog/` to this app's own `index.html`. Without that rewrite every link
 * below the index is a 404 on reload, and nothing in this class can tell.
 */
class BrowserRouter : Router {

    /**
     * Read once. It cannot change without the document reloading, and reading it
     * per navigation would let a pushed URL redefine where the app thinks it is.
     */
    private val base: String = basePathOf(window.location.pathname)

    override var current: BlogRoute by mutableStateOf(read())
        private set

    init {
        // The back and forward buttons. The browser has already changed the URL
        // by the time this fires, so the address is again the thing to read.
        window.addEventListener("popstate") { current = read() }
    }

    override fun go(route: BlogRoute) {
        window.history.pushState(null, "", base + route.location())
        current = route
    }

    override fun replace(route: BlogRoute) {
        window.history.replaceState(null, "", base + route.location())
        current = route
    }

    /** The route the address bar currently describes. */
    private fun read(): BlogRoute = routeOf(
        path = window.location.pathname.removePrefix(base),
        query = window.location.search,
    )
}
