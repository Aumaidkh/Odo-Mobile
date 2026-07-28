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
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcPencil
import com.hopcape.odo.core.designsystem.icons.IcPlusLarge
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcTrash
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.CarAvatar
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_ca_add
import com.hopcape.odo.feature.garage.resources.gr_ca_edit
import com.hopcape.odo.feature.garage.resources.gr_ca_export
import com.hopcape.odo.feature.garage.resources.gr_ca_remove
import com.hopcape.odo.feature.garage.resources.gr_ca_switch
import com.hopcape.odo.feature.garage.resources.gr_ca_switch_more
import org.jetbrains.compose.resources.stringResource

/**
 * Car actions sheet (⋮ on the car card) — shown as
 * [com.hopcape.odo.core.navigation.OdoDestination.Garage.CarActions]. Each row
 * navigates on to its flow; the sheet chrome comes from the navigation layer.
 */
@Composable
internal fun CarActionsSheetContent(
    onEditCar: () -> Unit,
    onSwitchCar: () -> Unit,
    onAddCar: () -> Unit,
    onExport: () -> Unit,
    onRemoveCar: () -> Unit,
) {
    GarageSheet {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarAvatar()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Sample car header — real data arrives with the garage aggregation.
                OdoText("Maruti Swift VXI", style = OdoTheme.typography.heading, maxLines = 1)
                OdoText("MH 12 AB 1234", style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, maxLines = 1)
            }
        }
        ActionRow(IcPencil, stringResource(Res.string.gr_ca_edit), onClick = onEditCar)
        ActionRow(IcCar, stringResource(Res.string.gr_ca_switch), onClick = onSwitchCar, trailing = stringResource(Res.string.gr_ca_switch_more, 1))
        ActionRow(IcPlusLarge, stringResource(Res.string.gr_ca_add), onClick = onAddCar)
        ActionRow(IcShare, stringResource(Res.string.gr_ca_export), onClick = onExport)
        OdoDivider(Modifier.padding(vertical = OdoTheme.spacing.xs))
        ActionRow(IcTrash, stringResource(Res.string.gr_ca_remove), onClick = onRemoveCar, tint = OdoTheme.colors.danger)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null,
    tint: Color = OdoTheme.colors.text,
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
        OdoText(label, style = OdoTheme.typography.heading, color = tint, maxLines = 1, modifier = Modifier.weight(1f))
        if (trailing != null) {
            OdoText(trailing, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textMuted)
        }
    }
}
