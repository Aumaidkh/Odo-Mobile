package com.hopcape.odo.web.admin.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_common_retry
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import org.jetbrains.compose.resources.stringResource

/**
 * What a section shows before its first answer arrives.
 *
 * A skeleton rather than a spinner, and the reason is this app specifically: the
 * Wasm bundle takes tens of seconds to boot on a cold load, and a spinner over that
 * span reads as a hang. Bars in the shape of the table that is coming say the page
 * is assembling itself, and they occupy the space the real rows will, so nothing
 * jumps when the data lands.
 *
 * The same component carries the failure, because a failed load and a pending one
 * happen in the same place and a screen that handled them separately would grow two
 * empty states that drift apart.
 */
@Composable
fun LoadingPanel(
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    rows: Int = 5,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (message != null) {
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(message, style = AdminType.body, color = AdminTokens.textStrong)
                    if (onRetry != null) PrimaryAction(stringResource(Res.string.ad_common_retry), onRetry)
                }
            }
            return@Column
        }

        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Shimmer(Modifier.width(160.dp).height(13.dp))
                repeat(rows) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Widths vary per row so the block reads as content rather
                        // than as a loading graphic that happens to be striped.
                        Shimmer(Modifier.weight(if (index % 3 == 0) 2.4f else 2f).height(11.dp))
                        Shimmer(Modifier.weight(1f).height(11.dp))
                        Shimmer(Modifier.weight(if (index % 2 == 0) 0.8f else 1.2f).height(11.dp))
                    }
                }
            }
        }
    }
}

/**
 * One bar, with a highlight travelling across it.
 *
 * A moving gradient rather than a pulsing alpha: a whole panel of bars fading in
 * unison pulses like an alert, where a sweep reads as progress. The offsets are in
 * pixels against a fixed 1200f span, which is wider than the panel ever is, so the
 * highlight enters and leaves rather than looping visibly inside a narrow bar.
 */
@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(AdminTokens.field, AdminTokens.railHover, AdminTokens.field),
                    start = Offset(x, 0f),
                    end = Offset(x + 600f, 0f),
                ),
            ),
    )
}

/**
 * The dot that says a write is in flight.
 *
 * For the header, where a skeleton would be wrong — the data is already on screen
 * and only one row is changing. Pulses rather than sweeps, because at 7dp a sweep
 * is invisible.
 */
@Composable
fun BusyDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "busy")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(
        modifier
            .width(7.dp)
            .height(7.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(4.dp))
            .background(AdminTokens.accent),
    )
}

/** Spacer-sized shimmer for a metric card that has not arrived. */
@Composable
fun ShimmerBlock(height: Int, modifier: Modifier = Modifier) {
    Shimmer(modifier.fillMaxWidth().height(height.dp))
    Spacer(Modifier.height(0.dp))
}
