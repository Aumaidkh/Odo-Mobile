package com.hopcape.odo.feature.autoodometer.presentation.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssurance
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssuranceKind
import com.hopcape.odo.core.designsystem.component.OdoPermissionRationale
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_cd_close
import com.hopcape.odo.feature.autoodometer.resources.ao_education_body_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_body_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_cta_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_cta_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_dismiss
import com.hopcape.odo.feature.autoodometer.resources.ao_education_keeps_distance
import com.hopcape.odo.feature.autoodometer.resources.ao_education_keeps_label
import com.hopcape.odo.feature.autoodometer.resources.ao_education_keeps_no_idle_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_keeps_no_idle_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_keeps_no_route
import com.hopcape.odo.feature.autoodometer.resources.ao_education_keeps_no_upload
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step1_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step1_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step2
import com.hopcape.odo.feature.autoodometer.resources.ao_education_step3
import com.hopcape.odo.feature.autoodometer.resources.ao_education_title
import com.hopcape.odo.feature.autoodometer.resources.ao_flow_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Your reading stays current on its own" — the how-it-works and privacy explainer (M2).
 *
 * The page that decides whether the owner wants the feature at all, so it names no permission.
 * What it does instead is state the trade plainly — one ticked line for what Odo keeps, three
 * crossed ones for what it never touches — and then show the mechanism as three numbered steps,
 * because "it measures the drive" is a claim and "start, measure, tick up" is a thing you can
 * picture.
 *
 * The copy is [state]-driven: the STEREO path explains the bonded-stereo trigger, the NO_STEREO
 * path (the device picker's "no Bluetooth" escape hatch) swaps step 1 and the "never tracked"
 * line for the motion-detection story (docs/AUTO_ODOMETER_PLAN.md §1.1).
 *
 * Its close is a cross rather than an arrow, and its dismiss says "not now": leaving here
 * abandons the whole feature rather than stepping back one screen, and the top bar should not
 * imply otherwise.
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
    OdoPermissionRationale(
        modifier = modifier,
        icon = IcCar,
        title = stringResource(Res.string.ao_education_title),
        subtitle = stringResource(bodyFor(state.mode)),
        benefits = emptyList(),
        assurancesLabel = stringResource(Res.string.ao_education_keeps_label),
        assurances = listOf(
            included(stringResource(Res.string.ao_education_keeps_distance)),
            excluded(stringResource(Res.string.ao_education_keeps_no_route)),
            excluded(stringResource(neverTrackedFor(state.mode))),
            excluded(stringResource(Res.string.ao_education_keeps_no_upload)),
        ),
        confirmLabel = stringResource(ctaFor(state.mode)),
        onConfirm = onCtaClick,
        dismissLabel = stringResource(Res.string.ao_education_dismiss),
        onDismiss = onClose,
        screenTitle = stringResource(Res.string.ao_flow_title),
        onBack = onClose,
        backContentDescription = stringResource(Res.string.ao_cd_close),
        navigationIcon = IcClose,
    ) {
        HowItWorksCard(state.mode)
    }
}

/**
 * The mechanism in three lines.
 *
 * Numbered rather than bulleted because the order is the explanation: nothing happens until the
 * car starts, and nothing is written until it stops. An owner who reads only this card has still
 * understood when Odo is and is not doing anything.
 */
@Composable
private fun HowItWorksCard(mode: TriggerMode) {
    OdoCard(modifier = Modifier.fillMaxWidth()) {
        StepRow(1, stringResource(step1For(mode)))
        OdoDivider()
        StepRow(2, stringResource(Res.string.ao_education_step2))
        OdoDivider()
        StepRow(3, stringResource(Res.string.ao_education_step3))
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(STEP_BADGE)
                .clip(OdoTheme.shapes.pill)
                .background(OdoTheme.colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            OdoText(
                text = number.toString(),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textDim,
            )
        }
        OdoText(text = text, style = OdoTheme.typography.bodySmall)
    }
}

private fun included(text: String) =
    OdoPermissionAssurance(text = text, kind = OdoPermissionAssuranceKind.Included)

private fun excluded(text: String) =
    OdoPermissionAssurance(text = text, kind = OdoPermissionAssuranceKind.Excluded)

private fun bodyFor(mode: TriggerMode): StringResource = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_education_body_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_education_body_no_stereo
}

private fun neverTrackedFor(mode: TriggerMode): StringResource = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_education_keeps_no_idle_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_education_keeps_no_idle_no_stereo
}

private fun step1For(mode: TriggerMode): StringResource = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_education_step1_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_education_step1_no_stereo
}

private fun ctaFor(mode: TriggerMode): StringResource = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_education_cta_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_education_cta_no_stereo
}

private val STEP_BADGE = 24.dp

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
