package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Fades [content] in and lifts it a little when it first appears, [index] steps after the
 * first item. Give a screen's blocks rising indices for a stagger.
 */
@Composable
fun OdoEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    visible: Boolean? = null,
    content: @Composable () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val easing = OdoTheme.motion.easeStandard
    val progress by animateFloatAsState(
        targetValue = if (visible ?: entered) 1f else 0f,
        animationSpec = tween(ENTRANCE_MILLIS, delayMillis = STAGGER_MILLIS * index, easing = easing),
        label = "odoEntrance$index",
    )
    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * RISE.toPx()
        },
    ) { content() }
}

private const val ENTRANCE_MILLIS = 420
private const val STAGGER_MILLIS = 60
private val RISE = 16.dp
