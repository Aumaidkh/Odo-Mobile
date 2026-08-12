package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Live capture markers: the outline of a detected paper, drawn as corner brackets joined
 * by a faint edge, over whatever preview this is stacked on.
 *
 * Takes plain normalized offsets (0..1 of its own bounds, clockwise from top-left) rather
 * than any camera type, so the component knows nothing about where the corners came from —
 * the screen does the frame-to-viewport mapping. Null means nothing is detected, and the
 * markers fade out rather than vanishing: detection flickers at the edge of its ability,
 * and a hard blink reads as breakage.
 *
 * The last known corners are kept for the fade-out. Drawing the fresh values while
 * fading *in* is what makes the brackets track the paper live.
 */
@Composable
fun OdoQuadMarkers(
    corners: List<Offset>?,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    var lastKnown by remember { mutableStateOf<List<Offset>?>(null) }
    if (corners != null && corners.size == QUAD_CORNERS) lastKnown = corners

    val visible = corners != null && corners.size == QUAD_CORNERS
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = FADE_MILLIS),
        label = "quadMarkers",
    )
    val drawn = (if (visible) corners else lastKnown) ?: return
    if (alpha <= 0.01f) return

    // Success once the owner pins the outline — the colour is the lock indicator.
    val accent = if (locked) OdoTheme.colors.success else OdoTheme.colors.accent
    Canvas(modifier) {
        val points = drawn.map { Offset(it.x * size.width, it.y * size.height) }
        val edgeStroke = 1.5.dp.toPx()
        val bracketStroke = 3.dp.toPx()
        val arm = 18.dp.toPx()

        val outline = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
            close()
        }
        drawPath(outline, accent.copy(alpha = EDGE_ALPHA * alpha), style = Stroke(edgeStroke))

        points.forEachIndexed { index, corner ->
            val previous = points[(index + points.size - 1) % points.size]
            val next = points[(index + 1) % points.size]
            drawLine(
                color = accent.copy(alpha = alpha),
                start = corner,
                end = corner + (next - corner).clampedTo(arm),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = accent.copy(alpha = alpha),
                start = corner,
                end = corner + (previous - corner).clampedTo(arm),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** This vector shortened to at most [length] — a bracket arm never spans a whole edge. */
private fun Offset.clampedTo(length: Float): Offset {
    val distance = getDistance()
    if (distance <= length || distance == 0f) return this
    return this * (length / distance)
}

private const val QUAD_CORNERS = 4
private const val EDGE_ALPHA = 0.35f
private const val FADE_MILLIS = 200
