package com.hopcape.odo.feature.profile.presentation.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcCurrencyDollar
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.presentation.Chevron
import com.hopcape.odo.feature.profile.presentation.IconTile
import com.hopcape.odo.feature.profile.presentation.ProfileSheet
import com.hopcape.odo.feature.profile.presentation.SectionLabel
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_done
import com.hopcape.odo.feature.profile.resources.pf_units
import com.hopcape.odo.feature.profile.resources.pf_units_currency
import com.hopcape.odo.feature.profile.resources.pf_units_distance
import com.hopcape.odo.feature.profile.resources.pf_units_fuel
import com.hopcape.odo.feature.profile.resources.pf_units_higher
import com.hopcape.odo.feature.profile.resources.pf_units_inr
import com.hopcape.odo.feature.profile.resources.pf_units_inr_summary
import com.hopcape.odo.feature.profile.resources.pf_units_km
import com.hopcape.odo.feature.profile.resources.pf_units_km_unit
import com.hopcape.odo.feature.profile.resources.pf_units_kmpl
import com.hopcape.odo.feature.profile.resources.pf_units_l100
import com.hopcape.odo.feature.profile.resources.pf_units_lower
import com.hopcape.odo.feature.profile.resources.pf_units_mi
import com.hopcape.odo.feature.profile.resources.pf_units_mi_unit
import org.jetbrains.compose.resources.stringResource

/**
 * Units & currency sheet ([com.hopcape.odo.core.navigation.OdoDestination.Profile.Units]).
 * UI-only: holds the distance/fuel selection; [onDone] pops the sheet.
 */
@Composable
internal fun UnitsCurrencySheetContent(onDone: () -> Unit, onOpenCurrency: () -> Unit) {
    var distanceKm by remember { mutableStateOf(true) }
    var fuelKmpl by remember { mutableStateOf(true) }
    ProfileSheet {
        OdoText(stringResource(Res.string.pf_units), style = OdoTheme.typography.heading)

        SectionLabel(stringResource(Res.string.pf_units_distance))
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            SelectCard(stringResource(Res.string.pf_units_km), "54,000", stringResource(Res.string.pf_units_km_unit), distanceKm) { distanceKm = true }
            SelectCard(stringResource(Res.string.pf_units_mi), "33,554", stringResource(Res.string.pf_units_mi_unit), !distanceKm) { distanceKm = false }
        }

        SectionLabel(stringResource(Res.string.pf_units_fuel))
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            SelectCard(stringResource(Res.string.pf_units_kmpl), "17.2", stringResource(Res.string.pf_units_higher), fuelKmpl) { fuelKmpl = true }
            SelectCard(stringResource(Res.string.pf_units_l100), "5.8", stringResource(Res.string.pf_units_lower), !fuelKmpl) { fuelKmpl = false }
        }

        SectionLabel(stringResource(Res.string.pf_units_currency))
        OdoCard(onClick = onOpenCurrency) {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                IconTile(IcCurrencyDollar)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OdoText(stringResource(Res.string.pf_units_inr), style = OdoTheme.typography.heading)
                    // Sample figures — real ones arrive with the cost-tracker read.
                    OdoText(
                        stringResource(Res.string.pf_units_inr_summary, "Rs. 12.3", "Rs. 8,240"),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
                Chevron()
            }
        }

        OdoButton(stringResource(Res.string.pf_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RowScope.SelectCard(label: String, value: String, unit: String, selected: Boolean, onClick: () -> Unit) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.6f) else OdoTheme.colors.border),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OdoText(label, style = OdoTheme.typography.caption, color = if (selected) accent else OdoTheme.colors.textDim, modifier = Modifier.weight(1f))
            if (selected) OdoIcon(IcCheck, contentDescription = null, tint = accent, size = OdoTheme.iconSizes.small)
        }
        OdoText(value, style = OdoTheme.typography.title, color = if (selected) OdoTheme.colors.text else OdoTheme.colors.textDim)
        OdoText(unit, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
    }
}
