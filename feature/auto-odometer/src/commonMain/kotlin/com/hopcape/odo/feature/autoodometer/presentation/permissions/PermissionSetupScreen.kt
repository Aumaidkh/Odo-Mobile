package com.hopcape.odo.feature.autoodometer.presentation.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoPermissionNudge
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBellFilled
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_cd_back
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_body
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_body
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_denied_row
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_body
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_notifications_body
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_notifications_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_open_settings
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_skip
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_step_progress
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "One last thing" — the staged permission checklist (M4).
 *
 * One step is on screen at a time: a priming card explaining what it is for, or — once the
 * owner has answered and it is still not granted — a denial row with a settings deep-link (and
 * a skip, for the one optional step). The primary CTA drives the whole sequence: it reads
 * "Finish setup" until the current step is permanently blocked, when it becomes "Open settings".
 *
 * State-free: renders [state] and forwards intents. The route host owns the platform
 * permission controllers and decides what [onContinue] actually triggers (a system dialog or
 * the settings page) — this screen only shows where the checklist is.
 */
@Composable
internal fun PermissionSetupScreen(
    state: PermissionSetupUiState,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.current
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.ao_permissions_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.ao_cd_back),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoButton(
                    text = if (state.currentBlocked) {
                        stringResource(Res.string.ao_permissions_open_settings)
                    } else {
                        stringResource(Res.string.ao_permissions_cta)
                    },
                    onClick = onContinue,
                    loading = state.completing,
                    enabled = current != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.showSkip) {
                    OdoButton(
                        text = stringResource(Res.string.ao_permissions_skip),
                        onClick = onSkip,
                        variant = OdoButtonVariant.Tertiary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
        ) {
            if (state.totalSteps > 0) {
                StepProgress(state.stepNumber, state.totalSteps)
            }
            if (current != null) {
                PrimingCard(current.step)
                if (state.showDenialRow) {
                    DenialRow(step = current.step, blocked = state.currentBlocked, onAction = onContinue)
                }
            }
        }
    }
}

@Composable
private fun StepProgress(stepNumber: Int, totalSteps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        OdoText(
            stringResource(Res.string.ao_permissions_step_progress, stepNumber, totalSteps),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textMuted,
        )
    }
}

@Composable
private fun PrimingCard(step: PermissionSetupStep) {
    OdoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            IconBadge(stepIcon(step))
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(stepTitle(step)), style = OdoTheme.typography.heading)
                // Body size, not bodySmall: this paragraph is the rationale the owner has to
                // read and act on before the system's own screen appears, usually standing at
                // the car. It is the copy the whole feature depends on, so it gets the
                // readable size rather than the supporting one.
                OdoText(
                    stringResource(stepBody(step)),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

@Composable
private fun DenialRow(step: PermissionSetupStep, blocked: Boolean, onAction: () -> Unit) {
    val stepLabel = stringResource(stepTitle(step))
    OdoPermissionNudge(
        icon = IcWarning,
        message = stringResource(Res.string.ao_permissions_denied_row, stepLabel),
        actionLabel = if (blocked) {
            stringResource(Res.string.ao_permissions_open_settings)
        } else {
            stringResource(Res.string.ao_permissions_cta)
        },
        // Mirrors the bottom bar's primary CTA — a second way to reach the same retry/settings
        // action from where the owner's eyes already are, not a separate path.
        onAction = onAction,
    )
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(ICON_BADGE_SIZE)
            .background(OdoTheme.colors.surfaceRaised, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(imageVector = icon, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
    }
}

private val ICON_BADGE_SIZE = 48.dp

private fun stepIcon(step: PermissionSetupStep): ImageVector = when (step) {
    PermissionSetupStep.NOTIFICATIONS -> IcBellFilled
    PermissionSetupStep.FINE_LOCATION -> IcCar
    PermissionSetupStep.BACKGROUND_LOCATION -> IcClock
    PermissionSetupStep.ACTIVITY_RECOGNITION -> IcSpeedometer
}

private fun stepTitle(step: PermissionSetupStep): StringResource = when (step) {
    PermissionSetupStep.NOTIFICATIONS -> Res.string.ao_permissions_notifications_title
    PermissionSetupStep.FINE_LOCATION -> Res.string.ao_permissions_location_title
    PermissionSetupStep.BACKGROUND_LOCATION -> Res.string.ao_permissions_background_title
    PermissionSetupStep.ACTIVITY_RECOGNITION -> Res.string.ao_permissions_activity_title
}

private fun stepBody(step: PermissionSetupStep): StringResource = when (step) {
    PermissionSetupStep.NOTIFICATIONS -> Res.string.ao_permissions_notifications_body
    PermissionSetupStep.FINE_LOCATION -> Res.string.ao_permissions_location_body
    PermissionSetupStep.BACKGROUND_LOCATION -> Res.string.ao_permissions_background_body
    PermissionSetupStep.ACTIVITY_RECOGNITION -> Res.string.ao_permissions_activity_body
}

@OdoThemePreviews
@Composable
private fun PermissionSetupScreenPrimingPreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.STEREO,
            steps = listOf(PermissionStepState(PermissionSetupStep.NOTIFICATIONS), PermissionStepState(PermissionSetupStep.FINE_LOCATION)),
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun PermissionSetupScreenMidSequencePreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.NO_STEREO,
            steps = listOf(
                PermissionStepState(PermissionSetupStep.NOTIFICATIONS, status = PermissionStatus.Granted),
                PermissionStepState(PermissionSetupStep.FINE_LOCATION),
                PermissionStepState(PermissionSetupStep.ACTIVITY_RECOGNITION),
            ),
            currentIndex = 1,
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun PermissionSetupScreenBlockedPreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.STEREO,
            steps = listOf(
                PermissionStepState(PermissionSetupStep.NOTIFICATIONS, status = PermissionStatus.Granted),
                PermissionStepState(PermissionSetupStep.FINE_LOCATION, status = PermissionStatus.Blocked, askedOnce = true),
            ),
            currentIndex = 1,
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun PermissionSetupScreenSkippableNudgePreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.STEREO,
            steps = listOf(
                PermissionStepState(PermissionSetupStep.NOTIFICATIONS, status = PermissionStatus.Askable, askedOnce = true),
                PermissionStepState(PermissionSetupStep.FINE_LOCATION),
            ),
            currentIndex = 0,
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}

@OdoThemePreviews
@Composable
private fun PermissionSetupScreenCompletingPreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.STEREO,
            steps = listOf(
                PermissionStepState(PermissionSetupStep.NOTIFICATIONS, status = PermissionStatus.Granted),
                PermissionStepState(PermissionSetupStep.FINE_LOCATION, status = PermissionStatus.Granted),
            ),
            currentIndex = 2,
            completing = true,
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}
