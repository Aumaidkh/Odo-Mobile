package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoOdometerEditor
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatKm
import com.hopcape.odo.feature.garage.domain.usecase.OdometerContext
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_odo_backspace
import com.hopcape.odo.feature.garage.resources.gr_odo_delta
import com.hopcape.odo.feature.garage.resources.gr_odo_last
import com.hopcape.odo.feature.garage.resources.gr_odo_save
import com.hopcape.odo.feature.garage.resources.gr_odo_subtitle
import com.hopcape.odo.feature.garage.resources.gr_odo_title
import com.hopcape.odo.feature.garage.resources.gr_odo_unit_km
import com.hopcape.odo.feature.garage.resources.gr_odo_unit_miles
import com.hopcape.odo.feature.garage.resources.gr_odometer
import org.jetbrains.compose.resources.stringResource

/**
 * Update-odometer sheet ([com.hopcape.odo.core.navigation.OdoDestination.Garage.UpdateOdometer]).
 *
 * The reading is captured by the design system's [OdoOdometerEditor] — the same drums,
 * km/miles toggle, quick-adds and keypad the owner met during onboarding — rather than a
 * plain number field. The sheet chrome itself comes from the Nav3 scene strategy, so the
 * editor is rendered bare and the garage's own context (what was last recorded, how far
 * the car has come since) goes in the editor's footer slot.
 *
 * A refused reading is shown in that footer rather than closing the sheet: the number the
 * owner just dialled is still there to be corrected.
 */
@Composable
internal fun UpdateOdometerSheetContent(
    state: UpdateOdometerUiState,
    onEvent: (UpdateOdometerEvent) -> Unit,
) {
    when (val context = state.context) {
        Loadable.Loading -> GarageSheet {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OdoLoadingIndicator()
            }
        }

        is Loadable.Failed -> GarageSheet {
            OdoText(
                context.message.asString(),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.danger,
            )
        }

        is Loadable.Ready -> Editor(context.value, state, onEvent)
    }
}

@Composable
private fun Editor(
    context: OdometerContext,
    state: UpdateOdometerUiState,
    onEvent: (UpdateOdometerEvent) -> Unit,
) {
    val recorded = context.lastRecorded
    OdoOdometerEditor(
        value = recorded.odometer.km.toLong(),
        onSave = { onEvent(UpdateOdometerEvent.Save(it)) },
        title = stringResource(Res.string.gr_odo_title),
        subtitle = stringResource(Res.string.gr_odo_subtitle),
        odometerLabel = stringResource(Res.string.gr_odometer),
        saveLabel = stringResource(Res.string.gr_odo_save),
        kmLabel = stringResource(Res.string.gr_odo_unit_km),
        milesLabel = stringResource(Res.string.gr_odo_unit_miles),
        backspaceLabel = stringResource(Res.string.gr_odo_backspace),
        footer = { dialled ->
            OdoText(
                stringResource(
                    Res.string.gr_odo_last,
                    recorded.odometer.formatKm(),
                    formatDate(recorded.date),
                ),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
            DistanceSince(context, dialled)
            state.submission.error?.let { message ->
                OdoText(
                    message.asString(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.danger,
                )
            }
        },
    )
}

/**
 * How far the car has come since it was last read, and roughly how far that is in a month.
 *
 * Absent until the drums are past the recorded reading — "+0 km since last update" is
 * noise — and the monthly figure is left out entirely when too little time has passed for
 * it to mean anything.
 */
@Composable
private fun DistanceSince(context: OdometerContext, dialled: Long?) {
    val km = dialled?.toInt() ?: return
    val since = context.kmSinceLastRecorded(km).takeIf { it > 0 } ?: return
    val perMonth = context.kmPerMonth(km) ?: return

    OdoCard(color = OdoTheme.colors.surfaceRaised) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(
                IcSpeedometer,
                contentDescription = null,
                tint = OdoTheme.colors.accent,
                size = OdoTheme.iconSizes.medium,
            )
            OdoText(
                stringResource(Res.string.gr_odo_delta, since.grouped(), perMonth.grouped()),
                style = OdoTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Thousands separated the way every other number in the app is written. */
private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
