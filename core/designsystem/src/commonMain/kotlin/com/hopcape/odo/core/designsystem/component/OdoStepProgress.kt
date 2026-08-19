package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Where the owner is in a flow that asks for several things in a row.
 *
 * One filled segment per step, and a count beside them. Both halves are needed: the segments
 * say how much is left at a glance, and the count says it in words for anyone who cannot read
 * a bar. A flow that asks for a sensitive permission has to be honest about its own length —
 * an owner who thinks they are on the last screen and finds a third one has been misled, and
 * that is the point at which people back out.
 *
 * ```
 * OdoStepProgress(current = 1, total = 3, label = stringResource(Res.string.rf_step_count, 1, 3))
 * ```
 *
 * @param current 1-based index of the step on screen.
 * @param total how many steps the flow has in all.
 * @param label the count in words, e.g. "1 of 3". The caller owns it because it is copy.
 */
@Composable
fun OdoStepProgress(
    current: Int,
    total: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (total <= 0) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The bar and the count say the same thing; a screen reader should hear it once.
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            repeat(total) { index ->
                Segment(filled = index < current, modifier = Modifier.weight(1f))
            }
        }
        OdoText(
            text = label,
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textDim,
        )
    }
}

/**
 * One step's worth of the bar: an empty track with a fill that sweeps across it.
 *
 * A sweep rather than a colour swap, which is what this was. Both animate, but a segment that
 * simply brightens says "this is now true" while one that fills says "you moved", and the second
 * is the thing the owner is looking at the bar to find out. It runs on the same timing as the
 * page slide beside it, so the two read as one movement rather than two.
 */
@Composable
private fun Segment(filled: Boolean, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = tween(
            durationMillis = OdoTheme.motion.flowMillis,
            easing = OdoTheme.motion.easeFlow,
        ),
        label = "segmentFill",
    )
    Box(
        modifier = modifier
            .height(SEGMENT_HEIGHT)
            .clip(OdoTheme.shapes.pill)
            .background(OdoTheme.colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill)
                .clip(OdoTheme.shapes.pill)
                .background(OdoTheme.colors.text),
        )
    }
}

private val SEGMENT_HEIGHT = 3.dp

@OdoThemePreviews
@Composable
private fun OdoStepProgressPreview() = OdoPreview {
    Box(modifier = Modifier.width(320.dp)) {
        OdoStepProgress(current = 2, total = 3, label = "2 of 3")
    }
}
