package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.shared.formatMonthYear
import com.hopcape.odo.feature.garage.domain.model.OdometerCorrection
import com.hopcape.odo.feature.garage.domain.model.RestoredHistory
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_hr_adopt
import com.hopcape.odo.feature.garage.resources.gr_hr_body
import com.hopcape.odo.feature.garage.resources.gr_hr_done
import com.hopcape.odo.feature.garage.resources.gr_hr_fuel
import com.hopcape.odo.feature.garage.resources.gr_hr_history_has
import com.hopcape.odo.feature.garage.resources.gr_hr_keep
import com.hopcape.odo.feature.garage.resources.gr_hr_odometer
import com.hopcape.odo.feature.garage.resources.gr_hr_only_forward
import com.hopcape.odo.feature.garage.resources.gr_hr_papers
import com.hopcape.odo.feature.garage.resources.gr_hr_service
import com.hopcape.odo.feature.garage.resources.gr_hr_since
import com.hopcape.odo.feature.garage.resources.gr_hr_title
import com.hopcape.odo.feature.garage.resources.gr_hr_view
import com.hopcape.odo.feature.garage.resources.gr_hr_you_entered
import org.jetbrains.compose.resources.stringResource

/**
 * "Purani history mil gayi" — what the owner sees when signing in brought their car's
 * records back (issue #425).
 *
 * Two shapes, one sheet. Without an odometer correction it is a welcome: what came back and
 * how far back it goes. With one it also carries the question, because the reading typed on
 * a fresh install is a guess and the history is evidence. The correction only ever appears
 * when the history is *higher* — [OdometerCorrection] cannot exist otherwise — so there is
 * no branch here that can move an odometer backwards.
 */
@Composable
internal fun RestoredHistorySheetContent(
    state: RestoredHistoryUiState,
    onEvent: (RestoredHistoryEvent) -> Unit,
) {
    val history = state.history ?: return
    GarageSheet(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        Header()
        RestoredSummary(history)
        history.odometer?.let { OdometerComparison(it) }
        state.submission.error?.let { message ->
            OdoText(
                message.asString(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.danger,
            )
        }
        Actions(history, state.submission.isInFlight, onEvent)
    }
}

/**
 * Icon beside the title rather than above it, matching the app-status sheet: a leading icon
 * lands the same distance from the title whatever the title's length.
 */
@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(
                IcClock,
                contentDescription = null,
                tint = OdoTheme.colors.accent,
                size = OdoTheme.iconSizes.large,
            )
            OdoText(
                stringResource(Res.string.gr_hr_title),
                style = OdoTheme.typography.title,
                color = OdoTheme.colors.text,
            )
        }
        OdoText(
            stringResource(Res.string.gr_hr_body),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

/** The plate, the car, and the three counts that say how much came back. */
@Composable
private fun RestoredSummary(history: RestoredHistory) {
    OdoCard(
        color = OdoTheme.colors.surfaceRaised,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            history.registrationNumber?.let { OdoBadge(it) }
            OdoText(
                history.carName,
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
        }
        OdoDivider()
        Row(modifier = Modifier.fillMaxWidth()) {
            Count(history.serviceLogCount, stringResource(Res.string.gr_hr_service))
            Count(history.documentCount, stringResource(Res.string.gr_hr_papers))
            Count(history.fuelFillCount, stringResource(Res.string.gr_hr_fuel))
        }
        history.since?.let { since ->
            OdoText(
                stringResource(Res.string.gr_hr_since, formatMonthYear(since)),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun RowScope.Count(value: Int, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        OdoText(value.toString(), style = OdoTheme.typography.numeric, color = OdoTheme.colors.text)
        OdoText(
            label.uppercase(),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
    }
}

/**
 * What the owner typed against what their history proves, with the rule stated underneath.
 *
 * The history's number is the larger of the two by construction, and it is set larger on
 * screen too — it is the answer being offered, not one of two equal options.
 */
@Composable
private fun OdometerComparison(correction: OdometerCorrection) {
    val distance = LocalOdoDistanceFormat.current
    OdoCard(
        color = OdoTheme.colors.surfaceRaised,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(
            stringResource(Res.string.gr_hr_odometer).uppercase(),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textMuted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            ComparisonRow(
                label = stringResource(Res.string.gr_hr_you_entered),
                value = distance.format(correction.currentKm),
                labelColor = OdoTheme.colors.textMuted,
                valueColor = OdoTheme.colors.textMuted,
                valueStyle = OdoTheme.typography.numeric,
            )
            ComparisonRow(
                label = stringResource(Res.string.gr_hr_history_has),
                value = distance.format(correction.historyKm),
                labelColor = OdoTheme.colors.textDim,
                valueColor = OdoTheme.colors.text,
                valueStyle = OdoTheme.typography.numeric.copy(fontSize = 32.sp, lineHeight = 34.sp),
            )
        }
        OdoText(
            stringResource(Res.string.gr_hr_only_forward),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textMuted,
        )
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    valueStyle: TextStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        OdoText(label, style = OdoTheme.typography.bodySmall, color = labelColor)
        OdoText(value, style = valueStyle, color = valueColor)
    }
}

/**
 * The primary action is whichever one the sheet is really about: taking the corrected
 * reading when there is one, otherwise going to look at what came back.
 */
@Composable
private fun Actions(
    history: RestoredHistory,
    inFlight: Boolean,
    onEvent: (RestoredHistoryEvent) -> Unit,
) {
    val distance = LocalOdoDistanceFormat.current
    val correction = history.odometer
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        if (correction != null) {
            OdoButton(
                stringResource(Res.string.gr_hr_adopt, distance.format(correction.historyKm)),
                onClick = { onEvent(RestoredHistoryEvent.AdoptOdometerTapped) },
                modifier = Modifier.fillMaxWidth(),
                loading = inFlight,
            )
            OdoButton(
                stringResource(Res.string.gr_hr_keep, distance.format(correction.currentKm)),
                onClick = { onEvent(RestoredHistoryEvent.DismissTapped) },
                modifier = Modifier.fillMaxWidth(),
                variant = OdoButtonVariant.Tertiary,
                enabled = !inFlight,
            )
        } else {
            OdoButton(
                stringResource(Res.string.gr_hr_view),
                onClick = { onEvent(RestoredHistoryEvent.ViewHistoryTapped) },
                modifier = Modifier.fillMaxWidth(),
            )
            OdoButton(
                stringResource(Res.string.gr_hr_done),
                onClick = { onEvent(RestoredHistoryEvent.DismissTapped) },
                modifier = Modifier.fillMaxWidth(),
                variant = OdoButtonVariant.Tertiary,
            )
        }
    }
}
