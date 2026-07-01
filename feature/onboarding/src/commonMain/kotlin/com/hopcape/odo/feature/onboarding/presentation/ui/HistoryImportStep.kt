package com.hopcape.odo.feature.onboarding.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEvent
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingUiState
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_history_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_history_title
import com.hopcape.odo.feature.onboarding.resources.onb_note_handwritten
import com.hopcape.odo.feature.onboarding.resources.onb_note_printed
import com.hopcape.odo.feature.onboarding.resources.onb_scan_cta
import com.hopcape.odo.feature.onboarding.resources.onb_scan_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Step 2 — the optional "scan your last service bill to auto-fill" hook (the app's
 * first WOW moment). The dashed tile dispatches [OnboardingEvent.ScanHistoryClicked];
 * scanning is stubbed in M1, so [OnboardingUiState.scanUnavailableMessage] surfaces
 * the "coming soon — Skip for now" nudge. The actual Scan / Skip buttons are the
 * screen's pinned footer.
 */
@Composable
internal fun HistoryImportStep(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
    ) {
        StepHeading(
            title = stringResource(Res.string.onb_history_title),
            subtitle = stringResource(Res.string.onb_history_subtitle),
        )

        ScanTile(onClick = { onEvent(OnboardingEvent.ScanHistoryClicked) })

        state.scanUnavailableMessage?.let { message ->
            OdoText(message.asString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.warning)
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            AccuracyNote(OnboardingIcons.Check, OdoTheme.colors.success, stringResource(Res.string.onb_note_printed))
            AccuracyNote(OnboardingIcons.Warning, OdoTheme.colors.warning, stringResource(Res.string.onb_note_handwritten))
        }
    }
}

/** The big dashed, tappable "Tap to scan a bill" tile. */
@Composable
private fun ScanTile(onClick: () -> Unit) {
    val accent = OdoTheme.colors.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.card)
            .background(OdoTheme.colors.surface)
            .dashedBorder(OdoTheme.colors.border, cornerRadius = CARD_RADIUS)
            .clickable(onClick = onClick)
            .padding(vertical = OdoTheme.spacing.xxxl, horizontal = OdoTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(OdoTheme.shapes.card)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(OnboardingIcons.Camera, contentDescription = null, tint = accent, size = 30.dp)
        }
        OdoText(stringResource(Res.string.onb_scan_cta), style = OdoTheme.typography.heading, color = OdoTheme.colors.text)
        OdoText(stringResource(Res.string.onb_scan_hint), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textMuted)
    }
}

/** One accuracy caveat line: a tinted glyph + dimmed copy. */
@Composable
private fun AccuracyNote(icon: ImageVector, tint: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.medium)
        OdoText(text, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
    }
}

/** Corner radius of the scan tile — mirrors `OdoTheme.shapes.card` (16dp) used for its clip. */
private val CARD_RADIUS = 16.dp

/** Draws a dashed rounded-rect outline on top of the content — the "drop a photo here" cue. */
private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, width: Dp = 1.5.dp): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
        )
    }

@OdoThemePreviews
@Composable
private fun HistoryImportStepPreview() = OdoPreview(padded = false) {
    OnboardingScreen(state = previewHistoryImportState(), onEvent = {})
}
