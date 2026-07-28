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
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.IconTile
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_rm_body
import com.hopcape.odo.feature.garage.resources.gr_rm_cancel
import com.hopcape.odo.feature.garage.resources.gr_rm_confirm
import com.hopcape.odo.feature.garage.resources.gr_rm_export_first
import com.hopcape.odo.feature.garage.resources.gr_rm_title
import org.jetbrains.compose.resources.stringResource

/**
 * Remove-car confirmation ([com.hopcape.odo.core.navigation.OdoDestination.Garage.RemoveCar]).
 * Destructive: leads with the impact + an "export first" escape hatch. Shown as a
 * sheet; [onCancel] / swipe-down dismiss it.
 */
@Composable
internal fun RemoveCarSheetContent(
    onExportFirst: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    GarageSheet {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            IconTile(IcTrash, tint = OdoTheme.colors.danger, size = 56.dp)
            // Sample car short name + counts — real values arrive with the ViewModel.
            OdoText(stringResource(Res.string.gr_rm_title, "Swift VXI"), style = OdoTheme.typography.title, textAlign = TextAlign.Center)
            OdoText(
                stringResource(Res.string.gr_rm_body, 4, 3),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
        }
        OdoButton(
            stringResource(Res.string.gr_rm_export_first),
            onClick = onExportFirst,
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Secondary,
            leadingIcon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
        OdoButton(
            stringResource(Res.string.gr_rm_confirm),
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Danger,
        )
        OdoButton(
            stringResource(Res.string.gr_rm_cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Tertiary,
        )
    }
}
