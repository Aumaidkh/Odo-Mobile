package com.hopcape.odo.feature.autoodometer.presentation.triplogged

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoConfirmDialog
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoOdometerDisplay
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.feature.autoodometer.resources.Res
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_cta_done
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_cta_reject
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_nudge_header
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_odometer_label
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_previous
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_reject_confirm_body
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_reject_confirm_cancel
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_reject_confirm_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_reject_confirm_title
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_row_distance
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_row_duration
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_row_route
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_row_route_value
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_subtitle
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_title
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_upgrade_body
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_upgrade_cta
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_upgrade_dismiss
import com.hopcape.odo.feature.autoodometer.resources.ao_trip_logged_upgrade_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Trip logged" — the odometer moment after a drive (M6): the drum, what was added, the
 * trip's stats, the service-due nudge when there is one, and the one-time background-location
 * upgrade card. State-free: renders [state] and forwards intents, exactly like
 * [com.hopcape.odo.feature.autoodometer.presentation.permissions.PermissionSetupScreen] —
 * the route host owns the `ACCESS_BACKGROUND_LOCATION` controller and decides what
 * [onUpgradeTapped] actually triggers.
 */
@Composable
internal fun TripLoggedScreen(
    state: TripLoggedUiState,
    onDone: () -> Unit,
    onReject: () -> Unit,
    onRejectConfirm: () -> Unit,
    onRejectDismiss: () -> Unit,
    onUpgradeTapped: () -> Unit,
    onUpgradeDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.ao_trip_logged_title),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoButton(
                    text = stringResource(Res.string.ao_trip_logged_cta_done),
                    onClick = onDone,
                    loading = state.acknowledging,
                    modifier = Modifier.fillMaxWidth(),
                )
                OdoButton(
                    text = stringResource(Res.string.ao_trip_logged_cta_reject),
                    onClick = onReject,
                    variant = OdoButtonVariant.Tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                OdoLoadingIndicator()
            }
            return@OdoScreen
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
        ) {
            OdometerHero(state)
            StatsCard(state)
            if (state.nudge != null) NudgeCard(state.nudge)
            if (state.backgroundUpgrade.visible) {
                UpgradeCard(onTapped = onUpgradeTapped, onDismissed = onUpgradeDismissed)
            }
        }
    }

    if (state.showRejectConfirm) {
        OdoConfirmDialog(
            title = stringResource(Res.string.ao_trip_logged_reject_confirm_title),
            body = stringResource(Res.string.ao_trip_logged_reject_confirm_body),
            confirmLabel = stringResource(Res.string.ao_trip_logged_reject_confirm_cta),
            cancelLabel = stringResource(Res.string.ao_trip_logged_reject_confirm_cancel),
            onConfirm = onRejectConfirm,
            onDismiss = onRejectDismiss,
        )
    }
}

@Composable
private fun OdometerHero(state: TripLoggedUiState) {
    val distance = LocalOdoDistanceFormat.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        OdoOdometerDisplay(
            value = distance.display(state.odometerNowKm ?: 0).toLong(),
            odometerLabel = stringResource(Res.string.ao_trip_logged_odometer_label),
        )
        if (state.odometerBeforeKm != null) {
            OdoText(
                stringResource(Res.string.ao_trip_logged_previous, distance.format(state.odometerBeforeKm)),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
            )
        }
        if (state.addedKm != null) {
            OdoText(
                stringResource(Res.string.ao_trip_logged_subtitle, distance.format(state.addedKm)),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun StatsCard(state: TripLoggedUiState) {
    val distance = LocalOdoDistanceFormat.current
    OdoCard {
        StatRow(
            label = stringResource(Res.string.ao_trip_logged_row_distance),
            value = state.addedKm?.let { distance.format(it) }.orEmpty(),
        )
        StatRow(
            label = stringResource(Res.string.ao_trip_logged_row_duration),
            value = state.duration?.asString().orEmpty(),
        )
        StatRow(
            label = stringResource(Res.string.ao_trip_logged_row_route),
            value = stringResource(Res.string.ao_trip_logged_row_route_value),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OdoText(label, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
        OdoText(value, style = OdoTheme.typography.body, color = OdoTheme.colors.text)
    }
}

@Composable
private fun NudgeCard(nudge: UiText) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        OdoText(
            stringResource(Res.string.ao_trip_logged_nudge_header),
            style = OdoTheme.typography.caption.copy(letterSpacing = 2.sp),
            color = OdoTheme.colors.textMuted,
        )
        OdoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
                OdoText(nudge.asString(), style = OdoTheme.typography.body, color = OdoTheme.colors.text)
            }
        }
    }
}

@Composable
private fun UpgradeCard(onTapped: () -> Unit, onDismissed: () -> Unit) {
    OdoCard {
        OdoText(stringResource(Res.string.ao_trip_logged_upgrade_title), style = OdoTheme.typography.heading)
        OdoText(
            stringResource(Res.string.ao_trip_logged_upgrade_body),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OdoButton(text = stringResource(Res.string.ao_trip_logged_upgrade_cta), onClick = onTapped)
            OdoButton(
                text = stringResource(Res.string.ao_trip_logged_upgrade_dismiss),
                onClick = onDismissed,
                variant = OdoButtonVariant.Tertiary,
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun TripLoggedScreenPreview() = OdoPreview(padded = false) {
    TripLoggedScreen(
        state = TripLoggedUiState(
            loading = false,
            odometerNowKm = 64231,
            odometerBeforeKm = 64167,
            addedKm = 64,
            backgroundUpgrade = BackgroundUpgradeCardState(granted = false),
        ),
        onDone = {},
        onReject = {},
        onRejectConfirm = {},
        onRejectDismiss = {},
        onUpgradeTapped = {},
        onUpgradeDismissed = {},
    )
}
