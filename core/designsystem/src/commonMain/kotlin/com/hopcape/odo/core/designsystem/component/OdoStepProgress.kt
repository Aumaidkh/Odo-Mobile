package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft

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
    // A flow's steps are often separate screens, each with its own bar. Starting from what the
    // last bar showed lets the new one fill (or drain) the segment that changed.
    val from = remember(total) { lastShownStep[total] ?: (current - 1).coerceAtLeast(0) }
    SideEffect { lastShownStep[total] = current }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The bar and the count say the same thing; a screen reader should hear it once.
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoSegmentedProgress(
            current = current,
            total = total,
            contentDescription = null,
            modifier = Modifier.weight(1f),
        )
        OdoText(
            text = label,
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textDim,
        )
    }
}

/**
 * The segments on their own, for a flow that states its position in the body rather than
 * beside the bar.
 *
 * Setup does exactly that — the step reads "STEP 3 OF 4" above the question — so a count
 * repeated in the header would be the same fact twice.
 *
 * @param contentDescription the position in words. Pass it when nothing else on the screen
 *   announces the count, and `null` when the caller already reads it out.
 */
@Composable
fun OdoSegmentedProgress(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (total <= 0) return
    // A flow's steps are often separate screens, each with its own bar. Starting from what the
    // last bar showed lets the new one fill (or drain) the segment that changed.
    val from = remember(total) { lastShownStep[total] ?: (current - 1).coerceAtLeast(0) }
    SideEffect { lastShownStep[total] = current }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        repeat(total) { index ->
            Segment(filled = index < current, startFilled = index < from, modifier = Modifier.weight(1f))
        }
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
private fun Segment(filled: Boolean, startFilled: Boolean, modifier: Modifier = Modifier) {
    val fill = remember { Animatable(if (startFilled) 1f else 0f) }
    val easing = OdoTheme.motion.easeStandard
    LaunchedEffect(filled) {
        // Delayed so the fill lands after the page transition rather than under it.
        fill.animateTo(if (filled) 1f else 0f, tween(SEGMENT_MILLIS, delayMillis = SEGMENT_DELAY_MILLIS, easing = easing))
    }
    Box(
        modifier = modifier
            .height(SEGMENT_HEIGHT)
            .clip(OdoTheme.shapes.pill)
            .background(OdoTheme.colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill.value)
                .clip(OdoTheme.shapes.pill)
                .background(OdoTheme.colors.text),
        )
    }
}

private val SEGMENT_HEIGHT = 3.dp
private const val SEGMENT_MILLIS = 450
private const val SEGMENT_DELAY_MILLIS = 150

/** The step each segmented bar last showed, keyed by its segment count. UI thread only. */
private val lastShownStep = mutableMapOf<Int, Int>()

@OdoThemePreviews
@Composable
private fun OdoStepProgressPreview() = OdoPreview {
    Box(modifier = Modifier.width(320.dp)) {
        OdoStepProgress(current = 2, total = 3, label = "2 of 3")
    }
}

@OdoThemePreviews
@Composable
private fun OdoSegmentedProgressPreview() = OdoPreview {
    Box(modifier = Modifier.width(320.dp)) {
        OdoSegmentedProgress(current = 3, total = 4, contentDescription = "Step 3 of 4")
    }
}

/**
 * The header of a multi-step flow: a back disc and the segmented progress in one row.
 * Every step of a flow uses it, so the chrome does not move between screens.
 */
@Composable
fun OdoStepHeader(
    current: Int,
    total: Int,
    onBack: (() -> Unit)?,
    backContentDescription: String,
    progressContentDescription: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        if (onBack != null) {
            OdoCircularIconButton(
                IcArrowLeft,
                contentDescription = backContentDescription,
                onClick = onBack,
                size = OdoTheme.spacing.minTouchTarget,
            )
        }
        OdoSegmentedProgress(
            current = current,
            total = total,
            modifier = Modifier.weight(1f),
            contentDescription = progressContentDescription,
        )
        trailing?.invoke()
    }
}
