package com.hopcape.odo.core.designsystem.preview

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark

/**
 * Renders the annotated preview in **both light and dark** [com.hopcape.odo.core
 * .designsystem.theme.OdoTheme], from a single annotation — no separate
 * `…LightPreview` / `…DarkPreview` functions. Wrap the body in [OdoPreview] so it
 * is themed:
 *
 * ```
 * @OdoThemePreviews
 * @Composable
 * private fun HealthDialPreview() = OdoPreview { HealthDial(score = 74) }
 * ```
 *
 * It composes the built-in [PreviewLightDark] multipreview (which toggles
 * `uiMode`); [OdoPreview]'s `isSystemInDarkTheme()` reads that to pick the
 * palette. This is the default annotation for component previews.
 */
@PreviewLightDark
annotation class OdoThemePreviews

/**
 * Accessibility sweep: light + dark **×** the system font-scale range. Use on
 * text-heavy components to catch truncation/clipping before users do. Heavier
 * than [OdoThemePreviews] (more panes), so reach for it deliberately.
 */
@PreviewLightDark
@PreviewFontScale
annotation class OdoFontScalePreviews

/**
 * Responsive check: the same preview at a compact phone width and an expanded
 * (tablet / large-foldable) width, so layouts are validated at both breakpoints.
 * Defaults to one theme; combine with [OdoPreview]`(darkTheme = …)` to pin it.
 */
@Preview(name = "Compact", widthDp = 360, group = "width")
@Preview(name = "Expanded", widthDp = 840, group = "width")
annotation class OdoWidthPreviews
