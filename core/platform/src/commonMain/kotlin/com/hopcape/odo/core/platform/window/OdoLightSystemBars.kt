package com.hopcape.odo.core.platform.window

import androidx.compose.runtime.Composable

/**
 * Forces light status-bar icons for as long as the calling screen is on show, and puts them
 * back on the way out.
 *
 * The app draws edge-to-edge and lets the system pick icon colour from the active theme,
 * which is right for every screen that follows the theme. A screen that commits to a dark
 * background whatever the theme says — the video intro — breaks that: in light mode the
 * system draws dark icons, and dark icons on black are invisible.
 *
 * Scoped rather than global on purpose. Setting it once at launch would leave every other
 * screen with the wrong icons in light mode.
 */
@Composable
expect fun OdoLightSystemBars()
