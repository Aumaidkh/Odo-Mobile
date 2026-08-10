package com.hopcape.odo.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Odo's brand colour tokens — the named `--odo-*` roles from the design spec,
 * exposed verbatim so screens reference intent (`OdoTheme.colors.accent`,
 * `OdoTheme.colors.textDim`) rather than Material's generic slots. This is the
 * brand-faithful surface; [androidx.compose.material3.ColorScheme] is the
 * compatibility layer underneath it.
 *
 * @property bg            screen background (`--odo-bg`)
 * @property surface       card / panel fill (`--odo-surface`)
 * @property surfaceRaised elevated surface — sheets, raised cards (`--odo-surface-raised`)
 * @property border        hairline dividers & outlines (`--odo-border`)
 * @property text          primary ink (`--odo-text`)
 * @property textDim       secondary ink — helper copy (`--odo-text-dim`)
 * @property textMuted     tertiary ink — disabled, captions (`--odo-text-muted`)
 * @property accent        odometer-orange — primary actions, FAB, brand (`--odo-accent`)
 * @property onAccent      ink on the accent fill (always white per spec)
 * @property success       verified · savings · good (`--odo-success`)
 * @property warning       overpay · expiring · low-confidence (`--odo-warning`)
 * @property danger        safety · expired · score < 50 (`--odo-danger`)
 * @property isDark        which theme produced this set (handy for asset/icon choice)
 */
@Immutable
data class OdoColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean,
)

 internal val DarkOdoColors = OdoColors(
    bg = OdoPalette.DarkBg,
    surface = OdoPalette.DarkSurface,
    surfaceRaised = OdoPalette.DarkSurfaceRaised,
    border = OdoPalette.DarkBorder,
    text = OdoPalette.DarkText,
    textDim = OdoPalette.DarkTextDim,
    textMuted = OdoPalette.DarkTextMuted,
    accent = OdoPalette.DarkAccent,
    onAccent = OdoPalette.OnAccent,
    success = OdoPalette.DarkSuccess,
    warning = OdoPalette.DarkWarning,
    danger = OdoPalette.DarkDanger,
    isDark = true,
)

internal val LightOdoColors = OdoColors(
    bg = OdoPalette.LightBg,
    surface = OdoPalette.LightSurface,
    surfaceRaised = OdoPalette.LightSurfaceRaised,
    border = OdoPalette.LightBorder,
    text = OdoPalette.LightText,
    textDim = OdoPalette.LightTextDim,
    textMuted = OdoPalette.LightTextMuted,
    accent = OdoPalette.LightAccent,
    onAccent = OdoPalette.OnAccent,
    success = OdoPalette.LightSuccess,
    warning = OdoPalette.LightWarning,
    danger = OdoPalette.LightDanger,
    isDark = false,
)

/**
 * Active [OdoColors] for the tree. `static` because the value only changes on a
 * full theme swap, not per recomposition. Defaults to the dark set — Odo's
 * brand-default appearance — so a read outside [OdoTheme] still renders on-brand.
 */
internal val LocalOdoColors = staticCompositionLocalOf { TeslaDarkOdoColors }

/**
 * Maps an Odo Health Score (0–100) onto a status colour, on the PRD §5.4 band
 * cut-offs, so the gauge, badges, and copy never disagree.
 *
 *  - `< 50`  → [danger]  (Poor / "Needs care")
 *  - `50–69` → [warning] (Fair)
 *  - `≥ 70`  → [success] (Good and Excellent — both are places to be happy about)
 */
fun OdoColors.healthScoreColor(score: Int): Color = when {
    score < 50 -> danger
    score < 70 -> warning
    else -> success
}
