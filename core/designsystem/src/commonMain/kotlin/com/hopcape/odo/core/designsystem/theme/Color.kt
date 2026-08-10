package com.hopcape.odo.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Odo's raw token palette — the single source of truth for every colour value in
 * the app (the `--odo-*` tokens from the design spec). Nothing else in the
 * codebase should hard-code a hex literal: screens read named roles from
 * [OdoTheme.colors] (brand tokens) or [OdoTheme.materialColors] (stock Material
 * components) instead.
 *
 * Odo is "the dashboard the whole product lives near" — a warm, near-black UI lit
 * by a single odometer-orange accent. The palette is **dark-first**; the light
 * set mirrors it (the accent is darkened to `#D35F28` so white text on it passes
 * AA contrast).
 *
 * Tokens are split: `Dark*` / `Light*` prefixes hold the two themes' values, and
 * the status colours (success/warning/danger) are shared roles that the product
 * leans on constantly — health score, fairness verdicts, verified badges.
 */
//internal object OdoPalette {
//    // ── Accent / brand (primary actions, FAB, brand) ────────────────────────
//    val DarkAccent = Color(0xFFE8743B)
//    val LightAccent = Color(0xFFD35F28) // darkened for AA contrast on white text
//    val OnAccent = Color(0xFFFFFFFF)
//
//    // ── Status — Verified·savings·good / Overpay·expiring·low-conf / Safety·expired·score<50
//    val DarkSuccess = Color(0xFF16C784)
//    val DarkWarning = Color(0xFFF5B301)
//    val DarkDanger = Color(0xFFEF5A43)
//    val LightSuccess = Color(0xFF0F9D63)
//    val LightWarning = Color(0xFFC98A00)
//    val LightDanger = Color(0xFFD23F2A)
//
//    // ── Dark surfaces & ink ─────────────────────────────────────────────────
//    val DarkBg = Color(0xFF15110D)
//    val DarkSurface = Color(0xFF211C17)
//    val DarkSurfaceRaised = Color(0xFF2B251E) // "Elevated"
//    val DarkBorder = Color(0xFF382F26)
//    val DarkText = Color(0xFFF5EDE4)
//    val DarkTextDim = Color(0xFFA89A89)
//    val DarkTextMuted = Color(0xFF6A5F52)
//
//    // ── Light surfaces & ink ────────────────────────────────────────────────
//    val LightBg = Color(0xFFFAF6F0)
//    val LightSurface = Color(0xFFFFFFFF)
//    val LightSurfaceRaised = Color(0xFFF0E9DF) // "Inset"
//    val LightBorder = Color(0xFFE6DCCF)
//    val LightText = Color(0xFF1F1813)
//    val LightTextDim = Color(0xFF6F6457)
//    val LightTextMuted = Color(0xFFA99D8C)
//}
//
///**
// * Material 3 scheme mapped from Odo tokens, so stock Material components (Button,
// * Card, TextField…) render on-brand. Brand-bespoke surfaces should prefer the
// * named [OdoColors] tokens; this exists so nothing ever falls back to Material's
// * default purple. `secondary`/`tertiary` carry the success/warning roles since
// * the brand has no separate secondary hue.
// */
//internal val DarkColorScheme = darkColorScheme(
//    primary = OdoPalette.DarkAccent,
//    onPrimary = OdoPalette.OnAccent,
//    primaryContainer = OdoPalette.DarkSurfaceRaised,
//    onPrimaryContainer = OdoPalette.DarkAccent,
//    secondary = OdoPalette.DarkSuccess,
//    onSecondary = OdoPalette.DarkBg,
//    secondaryContainer = OdoPalette.DarkSurfaceRaised,
//    onSecondaryContainer = OdoPalette.DarkSuccess,
//    tertiary = OdoPalette.DarkWarning,
//    onTertiary = OdoPalette.DarkBg,
//    tertiaryContainer = OdoPalette.DarkSurfaceRaised,
//    onTertiaryContainer = OdoPalette.DarkWarning,
//    error = OdoPalette.DarkDanger,
//    onError = OdoPalette.DarkBg,
//    errorContainer = OdoPalette.DarkSurfaceRaised,
//    onErrorContainer = OdoPalette.DarkDanger,
//    background = OdoPalette.DarkBg,
//    onBackground = OdoPalette.DarkText,
//    surface = OdoPalette.DarkSurface,
//    onSurface = OdoPalette.DarkText,
//    surfaceVariant = OdoPalette.DarkSurfaceRaised,
//    onSurfaceVariant = OdoPalette.DarkTextDim,
//    outline = OdoPalette.DarkBorder,
//    outlineVariant = OdoPalette.DarkBorder,
//    scrim = Color(0xFF000000),
//    inverseSurface = OdoPalette.DarkText,
//    inverseOnSurface = OdoPalette.DarkSurface,
//    inversePrimary = OdoPalette.LightAccent,
//)
//
//internal val LightColorScheme = lightColorScheme(
//    primary = OdoPalette.LightAccent,
//    onPrimary = OdoPalette.OnAccent,
//    primaryContainer = OdoPalette.LightSurfaceRaised,
//    onPrimaryContainer = OdoPalette.LightAccent,
//    secondary = OdoPalette.LightSuccess,
//    onSecondary = OdoPalette.OnAccent,
//    secondaryContainer = OdoPalette.LightSurfaceRaised,
//    onSecondaryContainer = OdoPalette.LightSuccess,
//    tertiary = OdoPalette.LightWarning,
//    onTertiary = OdoPalette.OnAccent,
//    tertiaryContainer = OdoPalette.LightSurfaceRaised,
//    onTertiaryContainer = OdoPalette.LightWarning,
//    error = OdoPalette.LightDanger,
//    onError = OdoPalette.OnAccent,
//    errorContainer = OdoPalette.LightSurfaceRaised,
//    onErrorContainer = OdoPalette.LightDanger,
//    background = OdoPalette.LightBg,
//    onBackground = OdoPalette.LightText,
//    surface = OdoPalette.LightSurface,
//    onSurface = OdoPalette.LightText,
//    surfaceVariant = OdoPalette.LightSurfaceRaised,
//    onSurfaceVariant = OdoPalette.LightTextDim,
//    outline = OdoPalette.LightBorder,
//    outlineVariant = OdoPalette.LightBorder,
//    scrim = Color(0xFF000000),
//    inverseSurface = OdoPalette.LightText,
//    inverseOnSurface = OdoPalette.LightSurface,
//    inversePrimary = OdoPalette.DarkAccent,
//)
