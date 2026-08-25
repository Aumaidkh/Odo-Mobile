package com.hopcape.odo.web.blog.ui.component

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens

/**
 * A placeholder in the shape of the thing that is coming.
 *
 * Deliberately not a spinner, for the reason [LoadableBox] already gives: a spinner
 * that shows for 200ms reads as a flicker. A skeleton does not move anything — the
 * bars sit exactly where the text will — and it only breathes, which is quiet enough
 * to survive being brief and informative enough to survive being slow.
 *
 * It also says something a line of text cannot: how much is coming.
 */
@Composable
fun SkeletonBar(
    widthFraction: Float = 1f,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val breath by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .alpha(breath)
            .clip(RoundedCornerShape(6.dp))
            .background(BlogThemeTokens.colors.surfaceRaised),
    )
}

/**
 * The default wait: a heading, a few lines, and a gap where the next thing starts.
 *
 * One shape for every screen rather than a bespoke skeleton each. A skeleton that
 * matched each page exactly would be a second copy of every layout, and the second
 * copy is the one that stops matching.
 */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBar(widthFraction = 0.55f, height = 28.dp)
        SkeletonBar(widthFraction = 0.95f)
        SkeletonBar(widthFraction = 0.88f)
        SkeletonBar(widthFraction = 0.62f)
        Box(Modifier.height(12.dp))
        SkeletonBar(widthFraction = 0.92f)
        SkeletonBar(widthFraction = 0.74f)
    }
}
