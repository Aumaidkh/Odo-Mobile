package com.hopcape.odo.feature.dashboard.presentation.shell

import androidx.compose.runtime.staticCompositionLocalOf
import com.hopcape.odo.core.designsystem.component.CoachMarkAnchorState

/**
 * The SCAN tile's coach-mark anchor, handed down from the shell to whichever destination
 * wants to point at it (#228 — Home does).
 *
 * A CompositionLocal because the two ends cannot reach each other any other way without
 * widening public APIs for one hook: the tile lives in the shell's bottom bar, the coach
 * mark is rendered by the Home destination inside the shell's content slot, and both sit
 * in this feature. `null` outside the shell (previews, tests), which simply means there
 * is nothing to point at.
 */
internal val LocalScanCoachMarkAnchor = staticCompositionLocalOf<CoachMarkAnchorState?> { null }
