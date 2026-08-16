package com.hopcape.odo.feature.profile.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.presentation.ProfileSheet
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_export
import com.hopcape.odo.feature.profile.resources.pf_export_account
import com.hopcape.odo.feature.profile.resources.pf_export_docs
import com.hopcape.odo.feature.profile.resources.pf_export_history
import com.hopcape.odo.feature.profile.resources.pf_export_note
import com.hopcape.odo.feature.profile.resources.pf_export_pdf
import com.hopcape.odo.feature.profile.resources.pf_export_sub
import org.jetbrains.compose.resources.stringResource

/**
 * Export-my-data sheet.
 *
 * The three lines are what an export contains, not choices: there is nothing to leave out
 * of a record whose point is being complete, and a checkbox that changed nothing would be
 * a control that lies.
 *
 * One button now, not two. Both used to open the paywall on the way to an export that did
 * not exist; the record PDF does exist, so this opens it. Whether the owner may have it is
 * the share sheet's to decide — it is the one place that knows, and it is reached from the
 * timeline and the ledger too.
 */
@Composable
internal fun ExportDataSheetContent(onExport: () -> Unit) {
    ProfileSheet {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.pf_export), style = OdoTheme.typography.heading)
            OdoText(stringResource(Res.string.pf_export_sub), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
        IncludedRow(stringResource(Res.string.pf_export_history))
        IncludedRow(stringResource(Res.string.pf_export_docs))
        IncludedRow(stringResource(Res.string.pf_export_account))
        OdoCard(color = OdoTheme.colors.surfaceRaised) {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OdoIcon(IcInfo, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
                OdoText(stringResource(Res.string.pf_export_note), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
        OdoButton(
            stringResource(Res.string.pf_export_pdf),
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IncludedRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.small)
        OdoText(label, style = OdoTheme.typography.body)
    }
}
