package com.hopcape.odo.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Tesla's raw token palette — the single source of truth for every colour value
 * in the app. Nothing else in the codebase should hard-code a hex literal:
 * screens read named roles from [OdoTheme.colors] (brand tokens) or
 * [OdoTheme.materialColors] (stock Material components) instead.
 *
 * Tesla is "cinematic restraint" — a black-and-white product frame where colour
 * carries no decorative load at all. The palette is **light-first**: near-white
 * surfaces, pure-black ink, and a single near-black `#171A20` accent that does
 * all the work of a brand colour. The dark set inverts it (the accent lifts to
 * white on black, since a near-black CTA is invisible on a near-black canvas).
 *
 * Tokens are split: `Light*` / `Dark*` prefixes hold the two themes' values, and
 * the status colours (success/warning/danger) are shared roles held deliberately
 * muted — they report state, they never style the UI.
 */
internal object TeslaPalette {
    // ── Accent / brand (primary actions, CTAs, brand) ───────────────────────
    val LightAccent = Color(0xFF171A20)
    val DarkAccent = Color(0xFFFFFFFF) // inverted: white CTA on a black canvas
    val OnLightAccent = Color(0xFFFFFFFF)
    val OnDarkAccent = Color(0xFF000000)

    // ── Status — Verified·good / Overpay·expiring / Safety·expired·recall ───
    val LightSuccess = Color(0xFF15803D)
    val LightWarning = Color(0xFFD97706)
    val LightDanger = Color(0xFFB91C1C)
    val DarkSuccess = Color(0xFF4ADE80)
    val DarkWarning = Color(0xFFFBBF24)
    val DarkDanger = Color(0xFFF87171)

    // ── Light surfaces & ink ────────────────────────────────────────────────
    val LightBg = Color(0xFFF9FAFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceRaised = Color(0xFFF4F4F4) // "Inset"
    val LightBorder = Color(0xFFE5E7EB)
    val LightText = Color(0xFF000000)
    val LightTextDim = Color(0xFF374151)
    val LightTextMuted = Color(0xFF6B7280)

    // ── Dark surfaces & ink ─────────────────────────────────────────────────
    val DarkBg = Color(0xFF000000)
    val DarkSurface = Color(0xFF111111)
    val DarkSurfaceRaised = Color(0xFF1C1C1C) // "Elevated"
    val DarkBorder = Color(0xFF2E2E2E)
    val DarkText = Color(0xFFFFFFFF)
    val DarkTextDim = Color(0xFFD1D5DB)
    val DarkTextMuted = Color(0xFF9CA3AF)
}

/**
 * Material 3 scheme mapped from Tesla tokens, so stock Material components
 * (Button, Card, TextField…) render on-brand. Brand-bespoke surfaces should
 * prefer the named [OdoColors] tokens; this exists so nothing ever falls back to
 * Material's default purple. `secondary`/`tertiary` carry the success/warning
 * roles since the brand has no separate secondary hue — the identity is
 * monochrome, and every accent slot resolves to the same near-black.
 */
internal val TeslaLightColorScheme = lightColorScheme(
    primary = TeslaPalette.LightAccent,
    onPrimary = TeslaPalette.OnLightAccent,
    primaryContainer = TeslaPalette.LightSurfaceRaised,
    onPrimaryContainer = TeslaPalette.LightAccent,
    secondary = TeslaPalette.LightSuccess,
    onSecondary = TeslaPalette.OnLightAccent,
    secondaryContainer = TeslaPalette.LightSurfaceRaised,
    onSecondaryContainer = TeslaPalette.LightSuccess,
    tertiary = TeslaPalette.LightWarning,
    onTertiary = TeslaPalette.OnLightAccent,
    tertiaryContainer = TeslaPalette.LightSurfaceRaised,
    onTertiaryContainer = TeslaPalette.LightWarning,
    error = TeslaPalette.LightDanger,
    onError = TeslaPalette.OnLightAccent,
    errorContainer = TeslaPalette.LightSurfaceRaised,
    onErrorContainer = TeslaPalette.LightDanger,
    background = TeslaPalette.LightBg,
    onBackground = TeslaPalette.LightText,
    surface = TeslaPalette.LightSurface,
    onSurface = TeslaPalette.LightText,
    surfaceVariant = TeslaPalette.LightSurfaceRaised,
    onSurfaceVariant = TeslaPalette.LightTextDim,
    outline = TeslaPalette.LightBorder,
    outlineVariant = TeslaPalette.LightBorder,
    scrim = Color(0xFF000000),
    inverseSurface = TeslaPalette.LightText,
    inverseOnSurface = TeslaPalette.LightSurface,
    inversePrimary = TeslaPalette.DarkAccent,
)

internal val TeslaDarkColorScheme = darkColorScheme(
    primary = TeslaPalette.DarkAccent,
    onPrimary = TeslaPalette.OnDarkAccent,
    primaryContainer = TeslaPalette.DarkSurfaceRaised,
    onPrimaryContainer = TeslaPalette.DarkAccent,
    secondary = TeslaPalette.DarkSuccess,
    onSecondary = TeslaPalette.DarkBg,
    secondaryContainer = TeslaPalette.DarkSurfaceRaised,
    onSecondaryContainer = TeslaPalette.DarkSuccess,
    tertiary = TeslaPalette.DarkWarning,
    onTertiary = TeslaPalette.DarkBg,
    tertiaryContainer = TeslaPalette.DarkSurfaceRaised,
    onTertiaryContainer = TeslaPalette.DarkWarning,
    error = TeslaPalette.DarkDanger,
    onError = TeslaPalette.DarkBg,
    errorContainer = TeslaPalette.DarkSurfaceRaised,
    onErrorContainer = TeslaPalette.DarkDanger,
    background = TeslaPalette.DarkBg,
    onBackground = TeslaPalette.DarkText,
    surface = TeslaPalette.DarkSurface,
    onSurface = TeslaPalette.DarkText,
    surfaceVariant = TeslaPalette.DarkSurfaceRaised,
    onSurfaceVariant = TeslaPalette.DarkTextDim,
    outline = TeslaPalette.DarkBorder,
    outlineVariant = TeslaPalette.DarkBorder,
    scrim = Color(0xFF000000),
    inverseSurface = TeslaPalette.DarkText,
    inverseOnSurface = TeslaPalette.DarkSurface,
    inversePrimary = TeslaPalette.LightAccent,
)
