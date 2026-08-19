package com.hopcape.odo.feature.autoodometer.presentation.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssurance
import com.hopcape.odo.core.designsystem.component.OdoPermissionAssuranceKind
import com.hopcape.odo.core.designsystem.component.OdoPermissionNudge
import com.hopcape.odo.core.designsystem.component.OdoPermissionRationale
import com.hopcape.odo.core.designsystem.component.OdoSystemHandoff
import com.hopcape.odo.core.designsystem.component.OdoSystemRow
import com.hopcape.odo.core.designsystem.component.OdoSystemRowControl
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcMapPin
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.triptracker.TriggerMode
import com.hopcape.odo.feature.autoodometer.domain.model.RecentDrive
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_background_handoff_body
import com.hopcape.odo.feature.autoodometer.resources.ao_background_handoff_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_background_handoff_note
import com.hopcape.odo.feature.autoodometer.resources.ao_background_handoff_screen
import com.hopcape.odo.feature.autoodometer.resources.ao_background_handoff_title
import com.hopcape.odo.feature.autoodometer.resources.ao_cd_back
import com.hopcape.odo.feature.autoodometer.resources.ao_drives_caught
import com.hopcape.odo.feature.autoodometer.resources.ao_drives_closed
import com.hopcape.odo.feature.autoodometer.resources.ao_drives_missed
import com.hopcape.odo.feature.autoodometer.resources.ao_drives_today
import com.hopcape.odo.feature.autoodometer.resources.ao_flow_title
import com.hopcape.odo.feature.autoodometer.resources.ao_handoff_eyebrow
import com.hopcape.odo.feature.autoodometer.resources.ao_handoff_preview_header
import com.hopcape.odo.feature.autoodometer.resources.ao_handoff_preview_label
import com.hopcape.odo.feature.autoodometer.resources.ao_location_handoff_body
import com.hopcape.odo.feature.autoodometer.resources.ao_location_handoff_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_location_handoff_instruction
import com.hopcape.odo.feature.autoodometer.resources.ao_location_handoff_note
import com.hopcape.odo.feature.autoodometer.resources.ao_location_handoff_screen
import com.hopcape.odo.feature.autoodometer.resources.ao_location_handoff_title
import com.hopcape.odo.feature.autoodometer.resources.ao_location_option_always
import com.hopcape.odo.feature.autoodometer.resources.ao_location_option_ask
import com.hopcape.odo.feature.autoodometer.resources.ao_location_option_deny
import com.hopcape.odo.feature.autoodometer.resources.ao_location_option_while_using
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_body
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_label
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_no_share
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_no_steps
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_yes_vehicle
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_activity_yes_window
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_body
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_body_history
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_label
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_no_idle_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_no_idle_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_no_route
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_revert
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_skip
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_background_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_denied_row
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_dismiss
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_body
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_label
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_no_route
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_no_share
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_title
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_yes_distance
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_yes_window_no_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_location_yes_window_stereo
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_open_settings
import com.hopcape.odo.feature.autoodometer.resources.ao_permissions_step_progress
import com.hopcape.odo.feature.autoodometer.resources.ao_step_next
import org.jetbrains.compose.resources.stringResource

/**
 * The staged permission flow (M4) — one ask per screen, in the order Android will accept them.
 *
 * Each step gets a page that says what that one permission is for and, in a ticked and crossed
 * list, what it does not reach. The two location asks then get a second page apiece, because
 * both end on a system screen the app does not control: Android offers them as a list of
 * mutually exclusive choices where the wrong row still looks like agreeing, and on Android 11+
 * the background one has no dialog at all. Drawing that list first is the only place saying
 * "pick the second one" does any good.
 *
 * State-free: renders [state] and forwards intents. The route host owns the platform permission
 * controllers and performs the ask when the ViewModel says to; this screen only shows where the
 * flow is.
 */
@Composable
internal fun PermissionSetupScreen(
    state: PermissionSetupUiState,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.current ?: return
    if (state.showHandoff) {
        HandoffPage(current.step, onContinue, onSkip, onBack, modifier)
        return
    }
    RationalePage(state, current.step, onContinue, onSkip, onBack, modifier)
}

/** A step's own page: the counter, what it is for, and what it does not reach. */
@Composable
private fun RationalePage(
    state: PermissionSetupUiState,
    step: PermissionSetupStep,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    OdoPermissionRationale(
        modifier = modifier,
        icon = step.icon(),
        title = stringResource(
            when (step) {
                PermissionSetupStep.FINE_LOCATION -> Res.string.ao_permissions_location_title
                PermissionSetupStep.BACKGROUND_LOCATION -> Res.string.ao_permissions_background_title
                PermissionSetupStep.ACTIVITY_RECOGNITION -> Res.string.ao_permissions_activity_title
            },
        ),
        subtitle = subtitleFor(step, state.recentDrives),
        benefits = emptyList(),
        assurancesLabel = stringResource(
            when (step) {
                PermissionSetupStep.FINE_LOCATION -> Res.string.ao_permissions_location_label
                PermissionSetupStep.BACKGROUND_LOCATION -> Res.string.ao_permissions_background_label
                PermissionSetupStep.ACTIVITY_RECOGNITION -> Res.string.ao_permissions_activity_label
            },
        ),
        assurances = assurancesFor(step, state.mode),
        confirmLabel = primaryLabel(step, state.currentBlocked),
        onConfirm = onContinue,
        // Only the optional step offers a way past it. On the others the dismiss leaves the
        // whole flow, which is what "not now" has always meant here.
        dismissLabel = if (state.showSkip) {
            stringResource(Res.string.ao_permissions_background_skip)
        } else {
            stringResource(Res.string.ao_permissions_dismiss)
        },
        onDismiss = if (state.showSkip) onSkip else onBack,
        screenTitle = stringResource(Res.string.ao_flow_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.ao_cd_back),
        stepCurrent = state.stepNumber,
        stepTotal = state.totalSteps,
        stepLabel = stringResource(
            Res.string.ao_permissions_step_progress,
            state.stepNumber,
            state.totalSteps,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.cardGap)) {
            // Their own record, and only where it is the argument being made. On a first-time
            // setup there is nothing here — the car has not moved yet.
            if (step == PermissionSetupStep.BACKGROUND_LOCATION && state.recentDrives.isNotEmpty()) {
                RecentDrivesCard(state.recentDrives)
            }
            if (state.showDenialRow) {
                DenialRow(step = step, blocked = state.currentBlocked, onAction = onContinue)
            }
        }
    }
}

/** The drawing of the system screen this step ends on, shown immediately before the handoff. */
@Composable
private fun HandoffPage(
    step: PermissionSetupStep,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val background = step == PermissionSetupStep.BACKGROUND_LOCATION
    OdoSystemHandoff(
        modifier = modifier,
        screenTitle = stringResource(
            if (background) {
                Res.string.ao_background_handoff_screen
            } else {
                Res.string.ao_location_handoff_screen
            },
        ),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.ao_cd_back),
        eyebrow = stringResource(Res.string.ao_handoff_eyebrow),
        title = stringResource(
            if (background) {
                Res.string.ao_background_handoff_title
            } else {
                Res.string.ao_location_handoff_title
            },
        ),
        body = stringResource(
            if (background) {
                Res.string.ao_background_handoff_body
            } else {
                Res.string.ao_location_handoff_body
            },
        ),
        // The background page has none: its drawing already shows the one row to pick, and a
        // card repeating that under a tick would be the same sentence twice.
        instruction = if (background) {
            null
        } else {
            stringResource(Res.string.ao_location_handoff_instruction)
        },
        previewLabel = stringResource(Res.string.ao_handoff_preview_label),
        previewHeader = stringResource(Res.string.ao_handoff_preview_header),
        previewRows = if (background) backgroundOptionRows() else fineLocationOptionRows(),
        previewNote = stringResource(
            if (background) {
                Res.string.ao_background_handoff_note
            } else {
                Res.string.ao_location_handoff_note
            },
        ),
        confirmLabel = stringResource(
            if (background) {
                Res.string.ao_background_handoff_cta
            } else {
                Res.string.ao_location_handoff_cta
            },
        ),
        onConfirm = onContinue,
        dismissLabel = if (background) {
            stringResource(Res.string.ao_permissions_background_skip)
        } else {
            stringResource(Res.string.ao_permissions_dismiss)
        },
        onDismiss = if (background) onSkip else onBack,
    )
}

/**
 * The choices Android offers for fine location, with the one to pick marked.
 *
 * "Ask every time" is drawn because it is the trap: it looks like agreeing, and it expires after
 * a single drive, so the owner would meet the same dialog again tomorrow and conclude the
 * feature is broken.
 */
@Composable
private fun fineLocationOptionRows(): List<OdoSystemRow> = listOf(
    optionRow(stringResource(Res.string.ao_location_option_always), selected = false),
    optionRow(stringResource(Res.string.ao_location_option_while_using), selected = true),
    optionRow(stringResource(Res.string.ao_location_option_ask), selected = false),
    optionRow(stringResource(Res.string.ao_location_option_deny), selected = false),
)

/** The same screen once fine location is held — Android drops "ask every time" from it. */
@Composable
private fun backgroundOptionRows(): List<OdoSystemRow> = listOf(
    optionRow(stringResource(Res.string.ao_location_option_always), selected = true),
    optionRow(stringResource(Res.string.ao_location_option_while_using), selected = false),
    optionRow(stringResource(Res.string.ao_location_option_deny), selected = false),
)

private fun optionRow(label: String, selected: Boolean) = OdoSystemRow(
    label = label,
    on = selected,
    control = OdoSystemRowControl.Radio,
    highlighted = selected,
)

/**
 * The owner's last few drives, and which of them Odo missed.
 *
 * The only argument for background location that is not a claim about the future. A missed drive
 * has no distance to show, because nothing was watching — which is the point, so the row says so
 * in words rather than showing a zero.
 */
@Composable
private fun RecentDrivesCard(drives: List<RecentDrive>) {
    val distance = LocalOdoDistanceFormat.current
    OdoCard(modifier = Modifier.fillMaxWidth()) {
        drives.forEachIndexed { index, drive ->
            if (index > 0) OdoDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OdoText(
                    text = if (drive.isToday) {
                        stringResource(Res.string.ao_drives_today)
                    } else {
                        drive.dayLabel
                    },
                    style = OdoTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                OdoText(
                    text = if (drive.caught) {
                        distance.format(drive.distanceKm)
                    } else {
                        stringResource(Res.string.ao_drives_missed)
                    },
                    style = OdoTheme.typography.label,
                    color = if (drive.caught) OdoTheme.colors.text else OdoTheme.colors.textMuted,
                )
                OdoText(
                    text = if (drive.caught) {
                        stringResource(Res.string.ao_drives_caught)
                    } else {
                        stringResource(Res.string.ao_drives_closed)
                    },
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun DenialRow(step: PermissionSetupStep, blocked: Boolean, onAction: () -> Unit) {
    val label = stringResource(
        when (step) {
            PermissionSetupStep.FINE_LOCATION -> Res.string.ao_permissions_location_title
            PermissionSetupStep.BACKGROUND_LOCATION -> Res.string.ao_permissions_background_title
            PermissionSetupStep.ACTIVITY_RECOGNITION -> Res.string.ao_permissions_activity_title
        },
    )
    OdoPermissionNudge(
        icon = IcWarning,
        message = stringResource(Res.string.ao_permissions_denied_row, label),
        actionLabel = if (blocked) {
            stringResource(Res.string.ao_permissions_open_settings)
        } else {
            stringResource(Res.string.ao_step_next)
        },
        // Mirrors the primary button — a second way to reach the same retry from where the
        // owner's eyes already are, not a separate path.
        onAction = onAction,
    )
}

/**
 * What this step's page says, which changes once the owner has drives to point at.
 *
 * Only the background step has two versions. Before there is any history the argument has to be
 * made in the abstract; afterwards it is simply what already happened to them.
 */
@Composable
private fun subtitleFor(step: PermissionSetupStep, drives: List<RecentDrive>): String = when {
    step == PermissionSetupStep.FINE_LOCATION ->
        stringResource(Res.string.ao_permissions_location_body)

    step == PermissionSetupStep.ACTIVITY_RECOGNITION ->
        stringResource(Res.string.ao_permissions_activity_body)

    drives.isEmpty() -> stringResource(Res.string.ao_permissions_background_body)
    else -> stringResource(Res.string.ao_permissions_background_body_history)
}

@Composable
private fun assurancesFor(
    step: PermissionSetupStep,
    mode: TriggerMode,
): List<OdoPermissionAssurance> = when (step) {
    PermissionSetupStep.FINE_LOCATION -> listOf(
        included(stringResource(Res.string.ao_permissions_location_yes_distance)),
        included(stringResource(windowLineFor(mode))),
        excluded(stringResource(Res.string.ao_permissions_location_no_route)),
        excluded(stringResource(Res.string.ao_permissions_location_no_share)),
    )

    // Crosses first, deliberately: this step is asking for more, so what it does *not* widen is
    // the answer to the question the owner is actually holding.
    PermissionSetupStep.BACKGROUND_LOCATION -> listOf(
        excluded(stringResource(idleLineFor(mode))),
        excluded(stringResource(Res.string.ao_permissions_background_no_route)),
        included(stringResource(Res.string.ao_permissions_background_revert)),
    )

    PermissionSetupStep.ACTIVITY_RECOGNITION -> listOf(
        included(stringResource(Res.string.ao_permissions_activity_yes_vehicle)),
        included(stringResource(Res.string.ao_permissions_activity_yes_window)),
        excluded(stringResource(Res.string.ao_permissions_activity_no_steps)),
        excluded(stringResource(Res.string.ao_permissions_activity_no_share)),
    )
}

/**
 * What the primary button does next.
 *
 * On a step that ends in a system screen it opens the drawing of that screen, so it says so
 * rather than promising a permission the tap does not ask for.
 */
@Composable
private fun primaryLabel(step: PermissionSetupStep, blocked: Boolean): String = when {
    blocked -> stringResource(Res.string.ao_permissions_open_settings)
    step.hasHandoff -> stringResource(Res.string.ao_step_next)
    else -> stringResource(Res.string.ao_permissions_activity_cta)
}

private fun windowLineFor(mode: TriggerMode) = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_permissions_location_yes_window_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_permissions_location_yes_window_no_stereo
}

private fun idleLineFor(mode: TriggerMode) = when (mode) {
    TriggerMode.STEREO -> Res.string.ao_permissions_background_no_idle_stereo
    TriggerMode.NO_STEREO -> Res.string.ao_permissions_background_no_idle_no_stereo
}

private fun PermissionSetupStep.icon(): ImageVector = when (this) {
    PermissionSetupStep.FINE_LOCATION -> IcMapPin
    PermissionSetupStep.BACKGROUND_LOCATION -> IcSpeedometer
    PermissionSetupStep.ACTIVITY_RECOGNITION -> IcSpeedometer
}

private fun included(text: String) =
    OdoPermissionAssurance(text = text, kind = OdoPermissionAssuranceKind.Included)

private fun excluded(text: String) =
    OdoPermissionAssurance(text = text, kind = OdoPermissionAssuranceKind.Excluded)

private fun steps(vararg step: PermissionSetupStep) = step.map { PermissionStepState(it) }

/** Step one of two — the ask the whole feature rests on. */
@OdoThemePreviews
@Composable
private fun PermissionSetupLocationPreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.STEREO,
            steps = steps(
                PermissionSetupStep.FINE_LOCATION,
                PermissionSetupStep.BACKGROUND_LOCATION,
            ),
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}

/** The drawing of the choice Android offers, with the row to pick marked. */
@OdoThemePreviews
@Composable
private fun PermissionSetupLocationHandoffPreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.STEREO,
            steps = steps(
                PermissionSetupStep.FINE_LOCATION,
                PermissionSetupStep.BACKGROUND_LOCATION,
            ),
            onHandoff = true,
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}

/** Step two, for an owner who declined it once and has the missed drives to show for it. */
@OdoThemePreviews
@Composable
private fun PermissionSetupBackgroundPreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.STEREO,
            steps = listOf(
                PermissionStepState(PermissionSetupStep.FINE_LOCATION, PermissionStatus.Granted),
                PermissionStepState(PermissionSetupStep.BACKGROUND_LOCATION),
            ),
            currentIndex = 1,
            recentDrives = listOf(
                RecentDrive(dayLabel = "18 Aug", isToday = true, distanceKm = 64, caught = true),
                RecentDrive(dayLabel = "16 Aug", isToday = false, distanceKm = 22, caught = true),
                RecentDrive(dayLabel = "12 Aug", isToday = false, distanceKm = 0, caught = false),
            ),
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}

/** Blocked on a load-bearing step: the button has to offer settings instead of asking again. */
@OdoThemePreviews
@Composable
private fun PermissionSetupBlockedPreview() = OdoPreview(padded = false) {
    PermissionSetupScreen(
        state = PermissionSetupUiState(
            mode = TriggerMode.NO_STEREO,
            steps = listOf(
                PermissionStepState(
                    PermissionSetupStep.FINE_LOCATION,
                    status = PermissionStatus.Blocked,
                    askedOnce = true,
                ),
                PermissionStepState(PermissionSetupStep.BACKGROUND_LOCATION),
                PermissionStepState(PermissionSetupStep.ACTIVITY_RECOGNITION),
            ),
        ),
        onContinue = {},
        onSkip = {},
        onBack = {},
    )
}
