package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCheckbox
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_ex_costs
import com.hopcape.odo.feature.garage.resources.gr_ex_docs
import com.hopcape.odo.feature.garage.resources.gr_ex_docs_count
import com.hopcape.odo.feature.garage.resources.gr_ex_pdf
import com.hopcape.odo.feature.garage.resources.gr_ex_service
import com.hopcape.odo.feature.garage.resources.gr_ex_service_count
import com.hopcape.odo.feature.garage.resources.gr_ex_share
import com.hopcape.odo.feature.garage.resources.gr_ex_subtitle
import com.hopcape.odo.feature.garage.resources.gr_ex_title
import org.jetbrains.compose.resources.stringResource

/**
 * Export-car-record sheet ([com.hopcape.odo.core.navigation.OdoDestination.Garage.Export]).
 * Holds the section toggles itself; [onPdf] / [onShare] are the terminal actions.
 */
@Composable
internal fun ExportSheetContent(onPdf: () -> Unit, onShare: () -> Unit) {
    var service by remember { mutableStateOf(true) }
    var documents by remember { mutableStateOf(true) }
    var costs by remember { mutableStateOf(false) }
    GarageSheet {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.gr_ex_title), style = OdoTheme.typography.heading)
            OdoText(stringResource(Res.string.gr_ex_subtitle), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
        ExportRow(stringResource(Res.string.gr_ex_service), stringResource(Res.string.gr_ex_service_count, 4), service) { service = it }
        ExportRow(stringResource(Res.string.gr_ex_docs), stringResource(Res.string.gr_ex_docs_count, 3), documents) { documents = it }
        ExportRow(stringResource(Res.string.gr_ex_costs), null, costs) { costs = it }
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            OdoButton(
                stringResource(Res.string.gr_ex_pdf),
                onClick = onPdf,
                modifier = Modifier.weight(1f),
                variant = OdoButtonVariant.Secondary,
                leadingIcon = { OdoIcon(IcPdf, contentDescription = null, size = OdoTheme.iconSizes.small) },
            )
            OdoButton(
                stringResource(Res.string.gr_ex_share),
                onClick = onShare,
                modifier = Modifier.weight(1f),
                leadingIcon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
            )
        }
    }
}

@Composable
private fun ExportRow(label: String, count: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        onClick = { onCheckedChange(!checked) },
        border = BorderStroke(1.dp, if (checked) accent.copy(alpha = 0.5f) else OdoTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoCheckbox(checked = checked, onCheckedChange = null)
            OdoText(label, style = OdoTheme.typography.heading, modifier = Modifier.weight(1f))
            if (count != null) OdoText(count, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }
}
