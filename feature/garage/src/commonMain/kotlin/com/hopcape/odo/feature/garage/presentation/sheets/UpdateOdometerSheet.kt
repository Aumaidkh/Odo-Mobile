package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoOdometerEditor
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_odo_delta
import com.hopcape.odo.feature.garage.resources.gr_odo_last
import com.hopcape.odo.feature.garage.resources.gr_odo_save
import com.hopcape.odo.feature.garage.resources.gr_odo_subtitle
import com.hopcape.odo.feature.garage.resources.gr_odo_title
import com.hopcape.odo.feature.garage.resources.gr_odo_unit_km
import com.hopcape.odo.feature.garage.resources.gr_odo_unit_miles
import com.hopcape.odo.feature.garage.resources.gr_odometer
import org.jetbrains.compose.resources.stringResource

/** Sample current reading, matching the garage card's sample car. */
private const val SampleReading = 54_120L

/**
 * Update-odometer sheet ([com.hopcape.odo.core.navigation.OdoDestination.Garage.UpdateOdometer]).
 *
 * The reading is captured by the design system's [OdoOdometerEditor] — the same drums,
 * km/miles toggle, quick-adds and keypad the owner met during onboarding — rather than a
 * plain number field. The sheet chrome itself comes from the Nav3 scene strategy, so the
 * editor is rendered bare and the garage's own context (what was last recorded, how far
 * the car has come since) goes in the editor's footer slot.
 *
 * [onSave] pops the sheet; persistence lands with the ViewModel.
 */
@Composable
internal fun UpdateOdometerSheetContent(onSave: () -> Unit) {
    OdoOdometerEditor(
        // Sample seed + context lines — real values arrive with the ViewModel.
        value = SampleReading,
        onSave = { onSave() },
        title = stringResource(Res.string.gr_odo_title),
        subtitle = stringResource(Res.string.gr_odo_subtitle),
        odometerLabel = stringResource(Res.string.gr_odometer),
        saveLabel = stringResource(Res.string.gr_odo_save),
        kmLabel = stringResource(Res.string.gr_odo_unit_km),
        milesLabel = stringResource(Res.string.gr_odo_unit_miles),
        footer = {
            OdoText(
                stringResource(Res.string.gr_odo_last, "48,500 km", "02 Mar 2026"),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
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
                        stringResource(Res.string.gr_odo_delta, "5,620", "1,400"),
                        style = OdoTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}
