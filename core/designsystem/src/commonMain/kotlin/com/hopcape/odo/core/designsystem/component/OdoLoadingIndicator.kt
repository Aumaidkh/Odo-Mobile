package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The brand's indeterminate spinner — an accent-tinted circular indicator for
 * "working, unknown duration" moments (a bill scan in flight, a fairness fetch).
 * When the amount *is* known, use [OdoProgressBar] instead.
 *
 * @param size the spinner's diameter; defaults to [OdoTheme.iconSizes] `large`.
 * @param color the arc colour; defaults to the brand accent.
 */
@Composable
fun OdoLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = OdoTheme.iconSizes.large,
    color: Color = OdoTheme.colors.accent,
    strokeWidth: Dp = 2.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
    )
}

/**
 * A full-bleed loading overlay — a dimming scrim over the screen with a centred
 * [OdoLoadingIndicator] and an optional [message] ("Scanning bill…"). Drop it on
 * top of content (e.g. as the last child of a `Box`) while a blocking async task
 * runs; it swallows taps so nothing underneath is interactive.
 *
 * ```
 * Box {
 *     BillReview(...)
 *     if (uiState.isScanning) OdoLoadingOverlay(message = "Scanning bill…")
 * }
 * ```
 *
 * @param message optional caption under the spinner.
 * @param scrimColor the dimming colour; defaults to the brand background at 60%.
 */
@Composable
fun OdoLoadingOverlay(
    modifier: Modifier = Modifier,
    message: String? = null,
    scrimColor: Color = OdoTheme.colors.bg.copy(alpha = 0.6f),
) {
    // Swallow taps (no ripple) so gestures never reach the covered UI while loading.
    val blockInteractions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor)
            .clickable(
                interactionSource = blockInteractions,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoLoadingIndicator()
            if (message != null) {
                OdoText(
                    message,
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoLoadingIndicatorPreview() = OdoPreview {
    OdoLoadingIndicator()
}
