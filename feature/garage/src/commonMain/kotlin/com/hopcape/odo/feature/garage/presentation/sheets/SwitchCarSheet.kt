package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcPlusLarge
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.CarAvatar
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_ca_add
import com.hopcape.odo.feature.garage.resources.gr_sw_title
import org.jetbrains.compose.resources.stringResource

/** One car in the switch-car sheet (sample content). */
private data class SwitchCarItem(val name: String, val subtitle: String, val selected: Boolean)

/**
 * Switch-car sheet ([com.hopcape.odo.core.navigation.OdoDestination.Garage.SwitchCar]).
 * [onSelect] switches the active car (pops the sheet); [onAddCar] opens the add-car flow.
 */
@Composable
internal fun SwitchCarSheetContent(onSelect: (String) -> Unit, onAddCar: () -> Unit) {
    val cars = listOf(
        SwitchCarItem("Maruti Swift VXI", "MH 12 AB 1234 · 54,000 km", selected = true),
        SwitchCarItem("Hyundai i20 Asta", "MH 12 CD 5678 · 22,300 km", selected = false),
    )
    GarageSheet {
        OdoText(stringResource(Res.string.gr_sw_title), style = OdoTheme.typography.heading)
        cars.forEach { car -> CarRow(car, onClick = { onSelect(car.name) }) }
        OdoButton(
            stringResource(Res.string.gr_ca_add),
            onClick = onAddCar,
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Secondary,
            leadingIcon = { OdoIcon(IcPlusLarge, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
    }
}

@Composable
private fun CarRow(car: SwitchCarItem, onClick: () -> Unit) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        onClick = onClick,
        border = BorderStroke(1.dp, if (car.selected) accent.copy(alpha = 0.6f) else OdoTheme.colors.border),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            CarAvatar(size = 44.dp, tint = if (car.selected) accent else OdoTheme.colors.textMuted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(car.name, style = OdoTheme.typography.heading, maxLines = 1)
                OdoText(car.subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, maxLines = 1)
            }
            if (car.selected) OdoIcon(IcCheck, contentDescription = null, tint = accent, size = OdoTheme.iconSizes.medium)
        }
    }
}
