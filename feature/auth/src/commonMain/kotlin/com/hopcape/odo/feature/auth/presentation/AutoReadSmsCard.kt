package com.hopcape.odo.feature.auth.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.auth.resources.Res
import com.hopcape.odo.feature.auth.resources.au_auto_read_filled
import com.hopcape.odo.feature.auth.resources.au_auto_read_listening
import com.hopcape.odo.feature.auth.resources.au_auto_read_title
import com.hopcape.odo.feature.auth.resources.au_auto_read_unavailable
import org.jetbrains.compose.resources.stringResource

/** What the SMS auto-reader is doing right now. */
internal enum class AutoReadSmsStatus {
    /** Listening for the incoming message. */
    Listening,

    /** The code arrived and was filled in without the owner typing. */
    Filled,

    /** No permission / no retriever — the owner has to type the code. */
    Unavailable,
}

/**
 * The "Auto Read SMS" element on the OTP screen — the reassurance that the owner does not
 * have to leave the app, watch the notification shade, and come back.
 *
 * Reads as one status object rather than a line of helper text: a haloed icon that pulses
 * while listening, a title, and a live sub-line. The halo and the trailing dots animate
 * **only** in [AutoReadSmsStatus.Listening]; the moment the code lands the whole card
 * settles to a static success state, which is what makes the fill feel finished rather
 * than merely quiet.
 *
 * Tints follow the same accent/success/border roles as the field it sits under, so the
 * two read as one surface.
 */
@Composable
internal fun AutoReadSmsCard(
    status: AutoReadSmsStatus,
    modifier: Modifier = Modifier,
) {
    val colors = OdoTheme.colors
    val accentFor = when (status) {
        AutoReadSmsStatus.Listening -> colors.accent
        AutoReadSmsStatus.Filled -> colors.success
        AutoReadSmsStatus.Unavailable -> colors.textMuted
    }
    val borderColor by animateColorAsState(
        targetValue = when (status) {
            AutoReadSmsStatus.Unavailable -> colors.border
            else -> accentFor.copy(alpha = 0.35f)
        },
        animationSpec = tween(OdoTheme.motion.baseMillis, easing = OdoTheme.motion.easeStandard),
        label = "autoReadBorder",
    )
    val subtitle = when (status) {
        AutoReadSmsStatus.Listening -> stringResource(Res.string.au_auto_read_listening)
        AutoReadSmsStatus.Filled -> stringResource(Res.string.au_auto_read_filled)
        AutoReadSmsStatus.Unavailable -> stringResource(Res.string.au_auto_read_unavailable)
    }
    val title = stringResource(Res.string.au_auto_read_title)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.card)
            .background(colors.surface)
            .border(1.dp, borderColor, OdoTheme.shapes.card)
            .padding(OdoTheme.spacing.cardPadding)
            // One announcement for the whole status, not three disconnected fragments.
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" },
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusGlyph(status = status, accent = accentFor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(title, style = OdoTheme.typography.label, color = colors.text)
            OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = colors.textDim)
        }
        if (status == AutoReadSmsStatus.Listening) {
            ListeningDots(color = accentFor)
        }
    }
}

/**
 * The leading glyph: an envelope (or a tick, once filled) inside a tinted disc, with a
 * halo that expands and fades out from underneath it while listening — a "receiving"
 * cue that costs one circle rather than an animation asset.
 */
@Composable
private fun StatusGlyph(status: AutoReadSmsStatus, accent: Color) {
    Box(
        modifier = Modifier.size(GlyphSize),
        contentAlignment = Alignment.Center,
    ) {
        if (status == AutoReadSmsStatus.Listening) {
            val transition = rememberInfiniteTransition(label = "autoReadHalo")
            val pulse by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(HaloDurationMillis, easing = LinearEasing),
                ),
                label = "autoReadHaloPulse",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .scale(1f + pulse * HaloGrowth)
                    .alpha((1f - pulse) * HaloPeakAlpha)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(
                if (status == AutoReadSmsStatus.Filled) IcCheck else IcEnvelope,
                contentDescription = null,
                tint = accent,
                size = OdoTheme.iconSizes.medium,
            )
        }
    }
}

/** Three dots breathing in sequence — the "still listening" tell, trailing the copy. */
@Composable
private fun ListeningDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "autoReadDots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(DotCount) { index ->
            val alpha by transition.animateFloat(
                initialValue = DotMinAlpha,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = DotDurationMillis,
                        // Stagger by index so the row reads left-to-right, not as one blink.
                        delayMillis = index * DotStaggerMillis,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "autoReadDot$index",
            )
            Box(
                Modifier
                    .size(DotSize)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private val GlyphSize = 36.dp
private val DotSize = 6.dp
private const val HaloDurationMillis = 1600
private const val HaloGrowth = 0.55f
private const val HaloPeakAlpha = 0.30f
private const val DotCount = 3
private const val DotDurationMillis = 420
private const val DotStaggerMillis = 140
private const val DotMinAlpha = 0.25f
