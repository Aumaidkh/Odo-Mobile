package com.hopcape.odo.feature.autoodometer.presentation.education

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_cd_close
import com.hopcape.odo.feature.autoodometer.resources.ao_education_cta_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_cta_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_privacy_header
import com.hopcape.odo.feature.autoodometer.resources.ao_education_privacy_kept
import com.hopcape.odo.feature.autoodometer.resources.ao_education_privacy_never_kept
import com.hopcape.odo.feature.autoodometer.resources.ao_education_privacy_never_tracked_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_privacy_never_tracked_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step1_body_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step1_body_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step1_title_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step1_title_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step2_body
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step2_title
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step3_body
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step3_title
import com.hopcape.odo.feature.autoodometer.resources.ao_education_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Your reading stays current on its own" — the how-it-works + privacy explainer (M2).
 *
 * Full screen, `X` closes. Copy is entirely [state]-driven: the STEREO path explains the
 * bonded-stereo trigger, the NO_STEREO path (the device picker's "no Bluetooth" escape
 * hatch) swaps step 1 and the privacy card's last line for the motion-detection story
 * (docs/AUTO_ODOMETER_PLAN.md §1.1). Nothing else in the layout changes between the two.
 *
 * State-free: renders [state] and forwards intents.
 */
@Composable
internal fun EducationScreen(
    state: EducationUiState,
    onCtaClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
            ) {
                OdoCircularIconButton(
                    imageVector = IcClose,
                    contentDescription = stringResource(Res.string.ao_cd_close),
                    onClick = onClose,
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.lg),
            ) {
                OdoButton(
                    text = ctaLabel(state.mode),
                    onClick = onCtaClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xxl),
        ) {
            OdoText(stringResource(Res.string.ao_education_title), style = OdoTheme.typography.display)

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
                StepRow(1, stringResource(step1Title(state.mode)), stringResource(step1Body(state.mode)))
                StepRow(2, stringResource(Res.string.ao_education_step2_title), stringResource(Res.string.ao_education_step2_body))
                StepRow(3, stringResource(Res.string.ao_education_step3_title), stringResource(Res.string.ao_education_step3_body))
            }

            PrivacyCard(state.mode)
        }
    }
}

@Composable
private fun StepRow(number: Int, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        StepBadge(number)
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(title, style = OdoTheme.typography.heading)
            OdoText(body, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }
}

@Composable
private fun StepBadge(number: Int) {
    OdoCard(
        modifier = Modifier
            .width(IntrinsicSize.Min),
        color = OdoTheme.colors.surfaceRaised,
        border = null,
        shape = OdoTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
    ) {
        OdoText(number.toString(), style = OdoTheme.typography.label, color = OdoTheme.colors.accent)
    }
}

@Composable
private fun PrivacyCard(mode: TriggerMode) {
    OdoCard {
        OdoText(
            stringResource(Res.string.ao_education_privacy_header),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        PrivacyLine(stringResource(Res.string.ao_education_privacy_kept), kept = true)
        PrivacyLine(stringResource(Res.string.ao_education_privacy_never_kept), kept = false)
        PrivacyLine(stringResource(neverTrackedLine(mode)), kept = false)
    }
}

@Composable
private fun PrivacyLine(text: String, kept: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Every line here reads as either kept or excluded, so both states share the
        // same tick/cross language the design system already uses for permission
        // assurances (OdoPermissionRationale) rather than inventing a third glyph.
        OdoIcon(
            imageVector = if (kept) IcCheck else IcClose,
            contentDescription = null,
            tint = if (kept) OdoTheme.colors.success else OdoTheme.colors.danger,
            size = OdoTheme.iconSizes.small,
        )
        OdoText(text, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
    }
}

private fun step1Title(mode: TriggerMode) = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_education_step1_title_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_education_step1_title_no_stereo
}

private fun step1Body(mode: TriggerMode) = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_education_step1_body_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_education_step1_body_no_stereo
}

private fun neverTrackedLine(mode: TriggerMode) = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_education_privacy_never_tracked_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_education_privacy_never_tracked_no_stereo
}

@Composable
private fun ctaLabel(mode: TriggerMode) = when (mode) {
    TriggerMode.STEREO -> stringResource(Res.string.ao_education_cta_stereo)
    TriggerMode.NO_STEREO -> stringResource(Res.string.ao_education_cta_no_stereo)
}

@OdoThemePreviews
@Composable
private fun EducationScreenStereoPreview() = OdoPreview(padded = false) {
    EducationScreen(state = EducationUiState(mode = TriggerMode.STEREO), onCtaClick = {}, onClose = {})
}

@OdoThemePreviews
@Composable
private fun EducationScreenNoStereoPreview() = OdoPreview(padded = false) {
    EducationScreen(state = EducationUiState(mode = TriggerMode.NO_STEREO), onCtaClick = {}, onClose = {})
}
