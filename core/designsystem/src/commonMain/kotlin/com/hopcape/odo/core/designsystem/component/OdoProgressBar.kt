package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A determinate, pill-capped progress bar — used for "part of a whole" readouts
 * like a Health Score component's contribution (e.g. Maintenance 30/35) or an
 * upload's progress. [progress] is a fraction in `0f..1f`; out-of-range values are
 * clamped, and a change animates smoothly.
 *
 * For *indeterminate* "working…" spinners use [OdoLoadingIndicator] instead — this
 * bar always represents a known amount.
 *
 * ```
 * OdoProgressBar(progress = earned.toFloat() / max, color = OdoTheme.colors.success)
 * ```
 *
 * @param color the filled portion's colour; defaults to the brand accent.
 * @param trackColor the unfilled track; defaults to the raised surface.
 * @param height the bar's thickness.
 */
@Composable
fun OdoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = OdoTheme.colors.accent,
    trackColor: Color = OdoTheme.colors.surfaceRaised,
    height: Dp = 8.dp,
) {
    val fraction by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = OdoTheme.motion.baseMillis,
            easing = OdoTheme.motion.easeStandard,
        ),
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(trackColor, OdoTheme.shapes.pill),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(color, OdoTheme.shapes.pill),
        )
    }
}

@OdoThemePreviews
@Composable
private fun OdoProgressBarPreview() = OdoPreview {
    OdoProgressBar(progress = 0.68f)
}
