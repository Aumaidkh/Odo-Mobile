package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.theme.healthScoreColor

/**
 * The Health Score gauge — a 270° arc (an odometer-like sweep, on brand) whose
 * fill tracks a 0–100 [score] and whose colour comes from the shared
 * [healthScoreColor] mapping (danger < 50, warning 50–79, success ≥ 80), so the
 * dial, badges, and copy never disagree. The [score] and an optional [label] band
 * ("Excellent" / "Good" / "Fair" / "Poor") sit in the centre.
 *
 * It renders only the number the caller passes; the **scoring rules** (35 pts
 * maintenance + 30 docs + 20 cost + 15 history) and the **band-label text** are
 * the feature's job — this component just visualises the result. The fill animates
 * when [score] changes.
 *
 * ```
 * OdoHealthDial(score = uiState.healthScore, label = uiState.healthBand)
 * ```
 *
 * @param label optional band word shown under the number (caller-supplied copy).
 * @param dialSize the square size of the gauge.
 * @param trackColor the unfilled arc colour; defaults to the brand border.
 */
@Composable
fun OdoHealthDial(
    score: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    dialSize: Dp = 176.dp,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = OdoTheme.colors.border,
) {
    val clamped = score.coerceIn(0, 100)
    val arcColor = OdoTheme.colors.healthScoreColor(clamped)
    // Animate the sweep so a recalculated score glides rather than jumps.
    val fraction by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(
            durationMillis = OdoTheme.motion.baseMillis,
            easing = OdoTheme.motion.easeStandard,
        ),
    )

    val startAngle = 135f    // bottom-left…
    val totalSweep = 270f    // …clockwise to bottom-right, leaving a 90° gap at the base.

    Box(modifier = modifier.size(dialSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(dialSize)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = totalSweep * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        ) {
            OdoText(
                clamped.toString(),
                style = OdoTheme.typography.display,
                color = arcColor,
            )
            if (label != null) {
                OdoText(
                    label,
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoHealthDialPreview() = OdoPreview {
    OdoHealthDial(score = 74, label = "Fair")
}
