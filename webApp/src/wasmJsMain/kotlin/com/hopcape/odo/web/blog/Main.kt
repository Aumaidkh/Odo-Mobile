package com.hopcape.odo.web.blog

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.hopcape.odo.web.blog.routing.BrowserRouter
import kotlinx.browser.document

/**
 * The blog's entry point.
 *
 * Compose takes over `<body>` rather than a nested container so the canvas is
 * the viewport — there is no surrounding page to lay out around. The boot
 * placeholder in `index.html` is removed here, at the first moment there is
 * something drawn to replace it; removing it any earlier would leave the reader
 * on a blank page for as long as the bundle takes to start.
 *
 * The router is built before composition and outlives it. It is the address bar,
 * and the address bar is not something a recomposition should be able to reset.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = checkNotNull(document.body) { "no <body> — index.html is not the page that loaded" }
    val router = BrowserRouter()
    document.getElementById("boot")?.remove()
    ComposeViewport(body) { BlogApp(router) }
}
