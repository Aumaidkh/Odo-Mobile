package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import kotlin.math.roundToInt

/**
 * Where a coach mark's anchor sits on screen, in window coordinates.
 *
 * The screen marks the thing being pointed at with [coachMarkAnchor]; [OdoCoachMark]
 * reads the bounds back. Window coordinates on purpose — the overlay is a [Popup] (its
 * own window, so it covers everything, bottom bar included), and window space is the one
 * space the anchor and the popup share regardless of where either sits in the tree.
 */
@Stable
class CoachMarkAnchorState {
    var boundsInWindow: Rect? by mutableStateOf(null)
        internal set
}

@Composable
fun rememberCoachMarkAnchorState(): CoachMarkAnchorState = remember { CoachMarkAnchorState() }

/** Marks this composable as the thing [OdoCoachMark] points at. */
fun Modifier.coachMarkAnchor(state: CoachMarkAnchorState): Modifier = onGloballyPositioned {
    state.boundsInWindow = it.boundsInWindow()
}

/**
 * One contextual hint (#226): a scrim over the whole window, a cutout around the anchor,
 * one line of copy, and a dismiss.
 *
 * Rendered as a [Popup] so it overlays everything — a screen inside the app scaffold's
 * content slot could never dim the bottom bar from within its own bounds, and the SCAN
 * button lives there. The popup is focusable, so system back dismisses it for free.
 *
 * Dismissible from anywhere: the scrim, the dismiss label, and system back all call
 * [onDismiss]. Tapping inside the cutout calls [onAnchorTap] when given (the "act on it"
 * path — the caller navigates and reports acted-on), otherwise it too dismisses. Nothing
 * here decides *whether* to show: the caller renders this only while it holds the
 * arbiter's grant, which is also why rotation and theme changes cannot re-show a
 * dismissed mark — the grant is state above this composable, not inside it.
 *
 * Copy comes from the caller, resolved ([OdoConfirmDialog]'s convention) — no literals
 * live in the component.
 *
 * Renders nothing until the anchor has reported real bounds — a coach mark pointing at a
 * guess is worse than one arriving a frame late.
 */
@Composable
fun OdoCoachMark(
    text: String,
    dismissLabel: String,
    anchor: CoachMarkAnchorState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onAnchorTap: (() -> Unit)? = null,
) {
    val anchorInWindow = anchor.boundsInWindow ?: return
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        CoachMarkContent(
            text = text,
            dismissLabel = dismissLabel,
            anchorInWindow = anchorInWindow,
            onDismiss = onDismiss,
            onAnchorTap = onAnchorTap,
            modifier = modifier,
        )
    }
}

/**
 * The popup's body, split out so previews can render it without a popup window.
 *
 * The popup's own root may not sit at the window origin (system bars), so the anchor's
 * window-space rect is translated into local space by the root's own window position
 * before anything is drawn against it.
 */
@Composable
private fun CoachMarkContent(
    text: String,
    dismissLabel: String,
    anchorInWindow: Rect,
    onDismiss: () -> Unit,
    onAnchorTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var rootOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    val anchorLocal = anchorInWindow.translate(-rootOriginInWindow)
    // Black in both themes, like every platform scrim — OdoColors carries no scrim token
    // and dimming is not a surface.
    val scrim = Color.Black.copy(alpha = SCRIM_ALPHA)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOriginInWindow = it.positionInWindow() }
            .pointerInput(anchorLocal, onAnchorTap == null) {
                detectTapGestures { offset ->
                    if (onAnchorTap != null && anchorLocal.inflate(CUTOUT_PADDING.toPx()).contains(offset)) {
                        onAnchorTap()
                    } else {
                        onDismiss()
                    }
                }
            }
            .drawBehind {
                val cutout = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = anchorLocal.inflate(CUTOUT_PADDING.toPx()),
                            cornerRadius = CornerRadius(CUTOUT_CORNER_RADIUS.toPx()),
                        ),
                    )
                }
                clipPath(cutout, ClipOp.Difference) { drawRect(scrim) }
            },
    ) {
        CoachMarkBubble(
            text = text,
            dismissLabel = dismissLabel,
            anchorLocal = anchorLocal,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The copy card, placed under the anchor when there is room and above it otherwise —
 * a bottom-bar anchor and a row mid-list both end up with the bubble on screen.
 */
@Composable
private fun CoachMarkBubble(
    text: String,
    dismissLabel: String,
    anchorLocal: Rect,
    onDismiss: () -> Unit,
) {
    Layout(
        content = {
            OdoCard(modifier = Modifier.widthIn(max = BUBBLE_MAX_WIDTH)) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    OdoText(text, style = OdoTheme.typography.body)
                    OdoText(
                        dismissLabel,
                        style = OdoTheme.typography.label,
                        color = OdoTheme.colors.accent,
                        modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = OdoTheme.spacing.xs),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bubble = measurables.first().measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val margin = BUBBLE_MARGIN.roundToPx()
            val gap = BUBBLE_GAP.roundToPx()
            val below = anchorLocal.bottom.roundToInt() + gap
            val fitsBelow = below + bubble.height + margin <= constraints.maxHeight
            val y = if (fitsBelow) below else anchorLocal.top.roundToInt() - gap - bubble.height
            val x = (anchorLocal.center.x.roundToInt() - bubble.width / 2)
                .coerceIn(margin, (constraints.maxWidth - bubble.width - margin).coerceAtLeast(margin))
            bubble.place(x, y.coerceAtLeast(margin))
        }
    }
}

private const val SCRIM_ALPHA = 0.6f
private val CUTOUT_PADDING = 8.dp
private val CUTOUT_CORNER_RADIUS = 16.dp
private val BUBBLE_MAX_WIDTH = 320.dp
private val BUBBLE_MARGIN = 16.dp
private val BUBBLE_GAP = 12.dp

@OdoThemePreviews
@Composable
private fun OdoCoachMarkBelowAnchorPreview() = OdoPreview(padded = false) {
    CoachMarkContent(
        text = "A photo of a bill becomes a logged service and a price check.",
        dismissLabel = "Got it",
        anchorInWindow = Rect(Offset(160f, 120f), Offset(360f, 220f)),
        onDismiss = {},
        onAnchorTap = {},
    )
}

@OdoThemePreviews
@Composable
private fun OdoCoachMarkAboveAnchorPreview() = OdoPreview(padded = false) {
    CoachMarkContent(
        text = "Updating the reading is what turns this into a real number.",
        dismissLabel = "Got it",
        anchorInWindow = Rect(Offset(120f, 1_700f), Offset(600f, 1_800f)),
        onDismiss = {},
        onAnchorTap = null,
    )
}
