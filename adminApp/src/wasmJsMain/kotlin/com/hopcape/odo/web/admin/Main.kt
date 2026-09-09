package com.hopcape.odo.web.admin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.hopcape.odo.web.admin.di.adminModule
import com.hopcape.odo.web.admin.routing.BrowserRouter
import kotlinx.browser.document
import org.koin.compose.KoinApplication

/**
 * The panel's entry point.
 *
 * The same shape as the blog's, for the same reasons: Compose takes over `<body>`
 * so the canvas is the viewport, the boot placeholder is removed at the first
 * moment there is something to replace it with, and the router is built before
 * composition because the address bar is not something a recomposition should be
 * able to reset.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = checkNotNull(document.body) { "no <body> — index.html is not the page that loaded" }
    val router = BrowserRouter()
    document.getElementById("boot")?.remove()
    ComposeViewport(body) {
        KoinApplication(application = { modules(adminModule) }) {
            AdminApp(router)
        }
    }
}
