package com.hopcape.odo.web.admin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The panel's skin.
 *
 * Its own, small, and deliberately not `:webApp`'s `BlogTheme`. That one is two
 * hand-drawn palettes for two audiences, wired through a `BlogThemeTokens` object
 * every blog screen references; moving it here would mean renaming that object
 * across every one of those screens for no gain today. When #370 moves the CMS
 * into this app, those screens come with it and the shared theme happens then —
 * once, in the slice that is already touching them.
 *
 * Plain Material 3 until that point. This is an internal tool: it needs to be
 * legible and consistent, not branded.
 */
@Composable
fun AdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DARK else LIGHT,
        typography = TYPOGRAPHY,
        content = content,
    )
}

/** Ink on paper, with one accent. Tables are the main thing here and they read better plain. */
private val LIGHT = lightColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF5A5A5A),
    outline = Color(0xFFD8D8D8),
    error = Color(0xFFB3261E),
)

private val DARK = darkColorScheme(
    primary = Color(0xFFEDEDED),
    onPrimary = Color(0xFF111111),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA8A8A8),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFF2B8B5),
)

/**
 * The system stack, not a downloaded face.
 *
 * A web font is a network round trip before the first legible frame, and this page
 * is opened by a handful of people who are waiting to do a task. `FontFamily.SansSerif`
 * resolves to whatever the machine already has.
 */
private val TYPOGRAPHY = Typography().run {
    val family = FontFamily.SansSerif
    copy(
        displaySmall = displaySmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = family, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}
