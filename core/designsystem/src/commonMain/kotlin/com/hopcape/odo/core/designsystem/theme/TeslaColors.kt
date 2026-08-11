package com.hopcape.odo.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val TeslaDarkOdoColors = OdoColors(
    bg = TeslaPalette.DarkBg,
    surface = TeslaPalette.DarkSurface,
    surfaceRaised = TeslaPalette.DarkSurfaceRaised,
    border = TeslaPalette.DarkBorder,
    text = TeslaPalette.DarkText,
    textDim = TeslaPalette.DarkTextDim,
    textMuted = TeslaPalette.DarkTextMuted,
    accent = TeslaPalette.DarkAccent,
    onAccent = TeslaPalette.OnDarkAccent,
    success = TeslaPalette.DarkSuccess,
    warning = TeslaPalette.DarkWarning,
    danger = TeslaPalette.DarkDanger,
    isDark = true,
)

internal val TeslaLightOdoColors = OdoColors(
    bg = TeslaPalette.LightBg,
    surface = TeslaPalette.LightSurface,
    surfaceRaised = TeslaPalette.LightSurfaceRaised,
    border = TeslaPalette.LightBorder,
    text = TeslaPalette.LightText,
    textDim = TeslaPalette.LightTextDim,
    textMuted = TeslaPalette.LightTextMuted,
    accent = TeslaPalette.LightAccent,
    onAccent = TeslaPalette.OnLightAccent,
    success = TeslaPalette.LightSuccess,
    warning = TeslaPalette.LightWarning,
    danger = TeslaPalette.LightDanger,
    isDark = false,
)

/**
 * Active [OdoColors] for the tree. `static` because the value only changes on a
 * full theme swap, not per recomposition. Defaults to the dark set — Odo's
 * brand-default appearance — so a read outside [OdoTheme] still renders on-brand.
 */
internal val LocalTeslaColors = staticCompositionLocalOf { TeslaDarkOdoColors }
