package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A block that stands in for content still being read, with a highlight sweeping across it.
 *
 * A skeleton rather than a spinner where the shape of the answer is known: a spinner says only
 * "wait", while a skeleton says what is about to appear and how much of it, which makes the
 * same wait feel shorter and stops the screen jumping when the content lands.
 *
 * The sweep is the point. A static grey block is indistinguishable from a rendering bug — the
 * movement is what says the screen is alive.
 */
@Composable
fun OdoShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = ShimmerDefaults.LineHeight,
    width: Dp? = null,
    shape: RoundedCornerShape = RoundedCornerShape(ShimmerDefaults.Radius),
) {
    val base = OdoTheme.colors.border
    // The highlight is the surface rather than white: on a light theme white would be
    // invisible against the block, and on a dark one it would flash.
    val highlight = OdoTheme.colors.surfaceRaised
    val progress = shimmerProgress()

    Box(
        modifier = modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .background(base, shape)
            .drawWithCache {
                // Twice the width, swept from off one edge to off the other, so the highlight
                // enters and leaves rather than appearing in the middle.
                val travel = size.width * SWEEP_TRAVEL
                val start = -size.width + travel * progress
                val brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + size.width, 0f),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = brush)
                }
            },
    )
}

/**
 * The rows a list is about to show, as blocks.
 *
 * [lines] is how many the caller expects — a guess is fine and better than none, because the
 * screen not changing height when the content arrives is most of what this buys.
 */
@Composable
fun OdoShimmerList(
    modifier: Modifier = Modifier,
    lines: Int = ShimmerDefaults.Lines,
    contentDescription: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Announced once for the whole block. A screen reader reading six identical
            // placeholders is worse than one that says the screen is loading.
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        repeat(lines) { index ->
            OdoShimmerBlock(
                // Alternating widths, because a stack of identical bars reads as a pattern
                // rather than as text about to arrive.
                width = null,
                height = if (index == 0) ShimmerDefaults.HeadlineHeight else ShimmerDefaults.LineHeight,
            )
        }
    }
}

/** One clock for every block on screen, so they sweep together rather than independently. */
@Composable
private fun shimmerProgress(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ShimmerDefaults.SweepMillis),
            // Restart, not reverse: a highlight that slides back the way it came reads as a
            // scrubber being dragged rather than as something loading.
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    return progress
}

/** How far the highlight travels, as a multiple of the block's width. */
private const val SWEEP_TRAVEL = 2f

object ShimmerDefaults {
    val LineHeight: Dp = 16.dp
    val HeadlineHeight: Dp = 28.dp
    val Radius: Dp = 6.dp
    const val Lines: Int = 4
    const val SweepMillis: Int = 1_200
}
