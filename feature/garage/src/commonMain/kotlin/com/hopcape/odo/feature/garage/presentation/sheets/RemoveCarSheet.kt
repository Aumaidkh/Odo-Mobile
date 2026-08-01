package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcTrash
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.IconTile
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.presentation.state.valueOrNull
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_rm_body
import com.hopcape.odo.feature.garage.resources.gr_rm_cancel
import com.hopcape.odo.feature.garage.resources.gr_rm_confirm
import com.hopcape.odo.feature.garage.resources.gr_rm_export_first
import com.hopcape.odo.feature.garage.resources.gr_rm_title
import org.jetbrains.compose.resources.stringResource

/**
 * Remove-car confirmation ([com.hopcape.odo.core.navigation.OdoDestination.Garage.RemoveCar]).
 *
 * Destructive, so it leads with what is actually about to go — the car's own name and its
 * real counts, read before the tap — plus an "export first" escape hatch. The remove button
 * stays disabled until those counts are known: a confirmation that cannot say what it
 * deletes is not a confirmation.
 */
@Composable
internal fun RemoveCarSheetContent(
    state: RemoveCarUiState,
    onEvent: (RemoveCarEvent) -> Unit,
) {
    val car = state.car.valueOrNull
    GarageSheet {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            IconTile(IcTrash, tint = OdoTheme.colors.danger, size = 56.dp)
            if (car != null) {
                OdoText(
                    stringResource(Res.string.gr_rm_title, car.displayName),
                    style = OdoTheme.typography.title,
                    textAlign = TextAlign.Center,
                )
                OdoText(
                    stringResource(Res.string.gr_rm_body, car.serviceCount, car.documentCount),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                )
            }
            state.submission.error?.let { message ->
                OdoText(
                    message.asString(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.danger,
                    textAlign = TextAlign.Center,
                )
            }
        }
        OdoButton(
            stringResource(Res.string.gr_rm_export_first),
            onClick = { onEvent(RemoveCarEvent.ExportFirstTapped) },
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Secondary,
            leadingIcon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
        OdoButton(
            stringResource(Res.string.gr_rm_confirm),
            onClick = { onEvent(RemoveCarEvent.RemoveTapped) },
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Danger,
            enabled = car != null && !state.submission.isInFlight,
        )
        OdoButton(
            stringResource(Res.string.gr_rm_cancel),
            onClick = { onEvent(RemoveCarEvent.CancelTapped) },
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Tertiary,
        )
    }
}
