package com.hopcape.odo.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale on a 4dp base grid (the SPACING + APPLIED LAYOUT tokens). The
 * first block is the raw step scale; the second is the spec's resolved layout
 * defaults, so screens stay consistent without re-deriving them.
 *
 * @property xs 4dp  — icon-to-label, inline gaps
 * @property sm 8dp  — chip padding, tight stacks
 * @property md 12dp — card gaps, button rows
 * @property lg 16dp — card padding, screen gutters
 * @property xl 24dp — screen padding, block gaps
 * @property xxl 32dp — major section breaks
 * @property xxxl 48dp — outsized vertical rhythm
 * @property screenEdge      18–24 → 20dp default screen horizontal padding
 * @property cardPadding     16dp inner card padding
 * @property cardGap         12dp gap between stacked cards
 * @property listRowVertical 11dp vertical padding for list rows
 * @property minTouchTarget  48dp — minimum tappable size (accessibility floor)
 */
@Immutable
data class OdoSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    val screenEdge: Dp = 20.dp,
    val cardPadding: Dp = 16.dp,
    val cardGap: Dp = 12.dp,
    val listRowVertical: Dp = 11.dp,
    val minTouchTarget: Dp = 48.dp,
)

internal val LocalOdoSpacing = staticCompositionLocalOf { OdoSpacing() }

/**
 * Elevation tiers. In Odo's dark UI, separation is carried mainly by surface
 * tints + borders ([OdoColors.surfaceRaised] / [OdoColors.border]); these dp
 * values map to Material `tonalElevation`/shadow where a component needs it.
 *
 * @property level0 0dp  — flush, separated by a border
 * @property level1 1dp  — cards
 * @property level2 3dp  — sheets, menus
 * @property level3 8dp  — device-frame / fullscreen overlays
 */
@Immutable
data class OdoElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 8.dp,
)

internal val LocalOdoElevation = staticCompositionLocalOf { OdoElevation() }

/**
 * Icon sizing tokens. `stroke` is the line weight for stroked/outline icons.
 *
 * @property stroke 1.8dp icon line weight
 * @property small  16dp inline / dense icons
 * @property medium 20dp default icons
 * @property large  24dp prominent / nav icons
 */
@Immutable
data class OdoIconSizes(
    val stroke: Dp = 1.8.dp,
    val small: Dp = 16.dp,
    val medium: Dp = 20.dp,
    val large: Dp = 24.dp,
)

internal val LocalOdoIconSizes = staticCompositionLocalOf { OdoIconSizes() }

/**
 * Motion tokens. Durations are milliseconds; [easeStandard] is the spec's
 * `cubic-bezier(.2, 0, 0, 1)` — a decelerate curve for entrances and transitions.
 *
 * @property fastMillis 150 — small/local state changes
 * @property baseMillis 250 — standard transitions
 */
@Immutable
data class OdoMotion(
    val fastMillis: Int = 150,
    val baseMillis: Int = 250,
    val easeStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)

internal val LocalOdoMotion = staticCompositionLocalOf { OdoMotion() }
