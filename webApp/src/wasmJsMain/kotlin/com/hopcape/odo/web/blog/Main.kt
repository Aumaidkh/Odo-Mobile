package com.hopcape.odo.web.blog

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/**
 * The blog's entry point.
 *
 * Compose takes over `<body>` rather than a nested container so the canvas is the
 * viewport — there is no surrounding page to lay out around. The boot placeholder
 * in `index.html` is removed here, at the first moment there is something drawn to
 * replace it; removing it any earlier would leave the reader on a blank page for
 * as long as the bundle takes to start.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = checkNotNull(document.body) { "no <body> — index.html is not the page that loaded" }
    document.getElementById("boot")?.remove()
    ComposeViewport(body) { BlogRoot() }
}

/**
 * Placeholder for the blog UI.
 *
 * The module exists to be built and deployed; what it draws is the next commit.
 * Kept deliberately small — one composable, no theme, no navigation — so none of
 * it has to be unpicked when the real screens land.
 */
@Composable
private fun BlogRoot() {
    // The web side of the brand is black and white; the app's warm palette stops
    // at the app. supabase/functions/legal/pages/layout.ts holds the real tokens.
    val dark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) Color.Black else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ODO",
            color = if (dark) Color.White else Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            // .34em tracking, the wordmark's setting everywhere else on odoapp.in.
            letterSpacing = 0.34.em,
            lineHeight = 1.2.em,
            textAlign = TextAlign.Center,
        )
    }
}
