package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A row of page dots for a paged flow (onboarding, a bill-scan tips carousel). The
 * [selectedIndex] dot animates from a round dot into an odometer-orange pill; the
 * rest stay small and dim, so position reads at a glance. Purely a display —
 * drive [selectedIndex] from your pager state.
 *
 * ```
 * OdoPageIndicator(pageCount = 3, selectedIndex = pagerState.currentPage)
 * ```
 *
 * The whole row carries a "Page X of N" description for screen readers, so the
 * individual dots stay decorative.
 *
 * @param activeColor the selected dot's colour (defaults to the brand accent).
 * @param inactiveColor the unselected dots' colour.
 * @param dotSize diameter of an unselected dot; the active pill keeps this height.
 * @param activeWidth width the selected dot grows to.
 */
@Composable
fun OdoPageIndicator(
    pageCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = OdoTheme.colors.accent,
    inactiveColor: Color = OdoTheme.colors.textMuted,
    dotSize: Dp = 8.dp,
    activeWidth: Dp = 22.dp,
    spacing: Dp = OdoTheme.spacing.sm,
) {
    val durationMillis = OdoTheme.motion.fastMillis
    val easing = OdoTheme.motion.easeStandard
    Row(
        modifier = modifier.semantics {
            contentDescription = "Page ${selectedIndex + 1} of $pageCount"
        },
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == selectedIndex
            val width by animateDpAsState(
                targetValue = if (selected) activeWidth else dotSize,
                animationSpec = tween(durationMillis, easing = easing),
            )
            val color by animateColorAsState(
                targetValue = if (selected) activeColor else inactiveColor,
                animationSpec = tween(durationMillis, easing = easing),
            )
            Box(
                Modifier
                    .height(dotSize)
                    .width(width)
                    .background(color, OdoTheme.shapes.pill),
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoPageIndicatorPreview() = OdoPreview {
    OdoPageIndicator(pageCount = 3, selectedIndex = 1)
}
