package com.hopcape.odo.feature.profile.presentation.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcCurrencyDollar
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.feature.profile.presentation.IconTile
import com.hopcape.odo.feature.profile.presentation.ProfileSheet
import com.hopcape.odo.feature.profile.presentation.SectionLabel
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_done
import com.hopcape.odo.feature.profile.resources.pf_units
import com.hopcape.odo.feature.profile.resources.pf_units_currency
import com.hopcape.odo.feature.profile.resources.pf_units_distance
import com.hopcape.odo.feature.profile.resources.pf_units_example_km
import com.hopcape.odo.feature.profile.resources.pf_units_example_kmpl
import com.hopcape.odo.feature.profile.resources.pf_units_example_l100
import com.hopcape.odo.feature.profile.resources.pf_units_example_mi
import com.hopcape.odo.feature.profile.resources.pf_units_fuel
import com.hopcape.odo.feature.profile.resources.pf_units_higher
import com.hopcape.odo.feature.profile.resources.pf_units_inr
import com.hopcape.odo.feature.profile.resources.pf_units_inr_only
import com.hopcape.odo.feature.profile.resources.pf_units_km
import com.hopcape.odo.feature.profile.resources.pf_units_km_unit
import com.hopcape.odo.feature.profile.resources.pf_units_kmpl
import com.hopcape.odo.feature.profile.resources.pf_units_l100
import com.hopcape.odo.feature.profile.resources.pf_units_lower
import com.hopcape.odo.feature.profile.resources.pf_units_mi
import com.hopcape.odo.feature.profile.resources.pf_units_mi_unit
import org.jetbrains.compose.resources.stringResource

/**
 * Units sheet: the distance unit every reading is shown and typed in, and how fuel
 * efficiency is written.
 *
 * The example figures on each card are the same reading converted both ways (54,000 km is
 * 33,554 mi; 15 km/l is 6.7 L/100km), so the choice is legible before it is made.
 *
 * Currency is one row with one option. Every amount Odo holds is in paise, and the
 * benchmarks and fuel prices it compares them against are Indian, so there is nothing to
 * convert to — the row says that rather than offering a choice that would change nothing.
 */
@Composable
internal fun UnitsCurrencySheetContent(
    state: UnitsUiState,
    onEvent: (UnitsEvent) -> Unit,
    onDone: () -> Unit,
) {
    ProfileSheet {
        OdoText(stringResource(Res.string.pf_units), style = OdoTheme.typography.heading)
        state.error?.let { message ->
            OdoText(message.asString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.danger)
        }

        SectionLabel(stringResource(Res.string.pf_units_distance))
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            SelectCard(
                label = stringResource(Res.string.pf_units_km),
                value = stringResource(Res.string.pf_units_example_km),
                unit = stringResource(Res.string.pf_units_km_unit),
                selected = state.distanceUnit == DistanceUnit.KILOMETRE,
                testTag = UnitsTestTags.distanceCard(DistanceUnit.KILOMETRE),
                onClick = { onEvent(UnitsEvent.DistanceUnitChosen(DistanceUnit.KILOMETRE)) },
            )
            SelectCard(
                label = stringResource(Res.string.pf_units_mi),
                value = stringResource(Res.string.pf_units_example_mi),
                unit = stringResource(Res.string.pf_units_mi_unit),
                selected = state.distanceUnit == DistanceUnit.MILE,
                testTag = UnitsTestTags.distanceCard(DistanceUnit.MILE),
                onClick = { onEvent(UnitsEvent.DistanceUnitChosen(DistanceUnit.MILE)) },
            )
        }

        SectionLabel(stringResource(Res.string.pf_units_fuel))
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            SelectCard(
                label = stringResource(Res.string.pf_units_kmpl),
                value = stringResource(Res.string.pf_units_example_kmpl),
                unit = stringResource(Res.string.pf_units_higher),
                selected = state.fuelEfficiencyUnit == FuelEfficiencyUnit.DISTANCE_PER_UNIT,
                testTag = UnitsTestTags.efficiencyCard(FuelEfficiencyUnit.DISTANCE_PER_UNIT),
                onClick = { onEvent(UnitsEvent.FuelEfficiencyUnitChosen(FuelEfficiencyUnit.DISTANCE_PER_UNIT)) },
            )
            SelectCard(
                label = stringResource(Res.string.pf_units_l100),
                value = stringResource(Res.string.pf_units_example_l100),
                unit = stringResource(Res.string.pf_units_lower),
                selected = state.fuelEfficiencyUnit == FuelEfficiencyUnit.UNITS_PER_100KM,
                testTag = UnitsTestTags.efficiencyCard(FuelEfficiencyUnit.UNITS_PER_100KM),
                onClick = { onEvent(UnitsEvent.FuelEfficiencyUnitChosen(FuelEfficiencyUnit.UNITS_PER_100KM)) },
            )
        }

        SectionLabel(stringResource(Res.string.pf_units_currency))
        OdoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                IconTile(IcCurrencyDollar)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OdoText(stringResource(Res.string.pf_units_inr), style = OdoTheme.typography.heading)
                    OdoText(
                        stringResource(Res.string.pf_units_inr_only),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
        }

        OdoButton(stringResource(Res.string.pf_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RowScope.SelectCard(
    label: String,
    value: String,
    unit: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        onClick = onClick,
        modifier = Modifier.weight(1f).testTag(testTag),
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

/** The four cards are labelled by unit, and two of those labels appear elsewhere on screen. */
object UnitsTestTags {
    fun distanceCard(unit: DistanceUnit): String = "profile_distance_${unit.name}"

    fun efficiencyCard(unit: FuelEfficiencyUnit): String = "profile_efficiency_${unit.name}"
}
