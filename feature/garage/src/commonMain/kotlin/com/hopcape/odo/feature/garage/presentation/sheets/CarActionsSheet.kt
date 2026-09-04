package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.formatRegistrationNumber
import com.hopcape.odo.core.designsystem.icons.IcPencil
import com.hopcape.odo.core.designsystem.icons.IcRupee
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcTrash
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.CarAvatar
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.valueOrNull
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_ca_edit
import com.hopcape.odo.feature.garage.resources.gr_ca_export
import com.hopcape.odo.feature.garage.resources.gr_ca_remove
import com.hopcape.odo.feature.garage.resources.gr_ca_value
import com.hopcape.odo.feature.garage.resources.gr_no_plate
import org.jetbrains.compose.resources.stringResource

/**
 * Car actions sheet (⋮ on the car card) — shown as
 * [com.hopcape.odo.core.navigation.OdoDestination.Garage.CarActions]. Each row navigates on
 * to its flow; the sheet chrome comes from the navigation layer.
 *
 * Switching cars and adding a second one are not here. The MVP garage holds one car, and a
 * menu offering a choice the app cannot honour is worse than a shorter menu.
 */
@Composable
internal fun CarActionsSheetContent(
    state: CarActionsUiState,
    onEvent: (CarActionsEvent) -> Unit,
) {
    GarageSheet {
        CarHeader(state.car)
        ActionRow(IcPencil, stringResource(Res.string.gr_ca_edit)) { onEvent(CarActionsEvent.EditTapped) }
        ActionRow(IcRupee, stringResource(Res.string.gr_ca_value)) { onEvent(CarActionsEvent.ValueTapped) }
        ActionRow(IcShare, stringResource(Res.string.gr_ca_export)) { onEvent(CarActionsEvent.ExportTapped) }
        OdoDivider(Modifier.padding(vertical = OdoTheme.spacing.xs))
        ActionRow(
            icon = IcTrash,
            label = stringResource(Res.string.gr_ca_remove),
            tint = OdoTheme.colors.danger,
        ) { onEvent(CarActionsEvent.RemoveTapped) }
    }
}

/** The car the actions are about. Absent until it has been read — never a stand-in name. */
@Composable
private fun CarHeader(car: Loadable<CarSummary>) {
    val summary = car.valueOrNull ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarAvatar()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(summary.displayName, style = OdoTheme.typography.heading, maxLines = 1)
            OdoText(
                summary.registration?.let(::formatRegistrationNumber)
                    ?: stringResource(Res.string.gr_no_plate),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = OdoTheme.colors.text,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.medium)
        OdoText(
            label,
            style = OdoTheme.typography.heading,
            color = tint,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}
