package com.hopcape.odo.feature.profile.presentation.sheets

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
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.presentation.ProfileSheet
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_export
import com.hopcape.odo.feature.profile.resources.pf_export_account
import com.hopcape.odo.feature.profile.resources.pf_export_docs
import com.hopcape.odo.feature.profile.resources.pf_export_history
import com.hopcape.odo.feature.profile.resources.pf_export_note
import com.hopcape.odo.feature.profile.resources.pf_export_pdf
import com.hopcape.odo.feature.profile.resources.pf_export_request
import com.hopcape.odo.feature.profile.resources.pf_export_sub
import org.jetbrains.compose.resources.stringResource

/**
 * Export-my-data sheet ([com.hopcape.odo.core.navigation.OdoDestination.Profile.Export]).
 * UI-only: holds the section toggles; [onDownloadPdf] / [onRequestExport] are terminal.
 */
@Composable
internal fun ExportDataSheetContent(onDownloadPdf: () -> Unit, onRequestExport: () -> Unit) {
    var history by remember { mutableStateOf(true) }
    var documents by remember { mutableStateOf(true) }
    var account by remember { mutableStateOf(false) }
    ProfileSheet {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.pf_export), style = OdoTheme.typography.heading)
            OdoText(stringResource(Res.string.pf_export_sub), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
        ExportRow(stringResource(Res.string.pf_export_history), history) { history = it }
        ExportRow(stringResource(Res.string.pf_export_docs), documents) { documents = it }
        ExportRow(stringResource(Res.string.pf_export_account), account) { account = it }
        OdoCard(color = OdoTheme.colors.surfaceRaised) {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OdoIcon(IcEnvelope, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
                OdoText(stringResource(Res.string.pf_export_note), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            OdoButton(stringResource(Res.string.pf_export_pdf), onClick = onDownloadPdf, modifier = Modifier.weight(1f), variant = OdoButtonVariant.Secondary)
            OdoButton(stringResource(Res.string.pf_export_request), onClick = onRequestExport, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExportRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        onClick = { onCheckedChange(!checked) },
        border = BorderStroke(1.dp, if (checked) accent.copy(alpha = 0.5f) else OdoTheme.colors.border),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OdoCheckbox(checked = checked, onCheckedChange = null)
            OdoText(label, style = OdoTheme.typography.heading, modifier = Modifier.weight(1f))
        }
    }
}
