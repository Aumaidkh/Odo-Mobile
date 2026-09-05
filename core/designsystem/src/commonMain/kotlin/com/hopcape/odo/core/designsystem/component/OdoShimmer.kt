package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
    val progress = LocalShimmerProgress.current

    Box(
        modifier = modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            // Clipped before it is filled, so the sweep cannot paint the corners the shape
            // rounds off. Drawn as a plain rect over an unclipped block, the highlight gave
            // the block square corners for as long as it was crossing them.
            .clip(shape)
            .background(base)
            .drawBehind {
                // Twice the width, swept from off one edge to off the other, so the highlight
                // enters and leaves rather than appearing in the middle.
                //
                // Read here rather than in composition: reading an animated value while
                // composing recomposes this block on every frame, which is the one thing a
                // placeholder must not cost.
                val travel = size.width * SWEEP_TRAVEL
                val start = -size.width + travel * progress.value
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(start + size.width, 0f),
                    ),
                )
            },
    )
}

/**
 * One clock for every block beneath it, so they sweep together.
 *
 * Wrap a loading screen in this. Without it each block starts its own animation from its own
 * composition time, so blocks that appear a moment apart sweep out of step — which reads as
 * several things loading rather than one screen.
 */
@Composable
fun OdoShimmerHost(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
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
    CompositionLocalProvider(LocalShimmerProgress provides progress, content = content)
}

/**
 * How far through the sweep every block is.
 *
 * A `State` rather than a `Float`, so a block reads it in its draw pass instead of being
 * recomposed sixty times a second. Outside a host it is a constant, and a block drawn there
 * is a plain grey placeholder rather than a crash.
 */
private val LocalShimmerProgress = compositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** How far the highlight travels, as a multiple of the block's width. */
private const val SWEEP_TRAVEL = 2f

object ShimmerDefaults {
    val LineHeight: Dp = 16.dp
    val HeadlineHeight: Dp = 28.dp
    val Radius: Dp = 6.dp
    const val SweepMillis: Int = 1_200
}
