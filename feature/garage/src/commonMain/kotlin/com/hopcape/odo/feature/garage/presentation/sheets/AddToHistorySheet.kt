package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.icons.IcPencil
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.IconTile
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_ah_doc
import com.hopcape.odo.feature.garage.resources.gr_ah_doc_sub
import com.hopcape.odo.feature.garage.resources.gr_ah_manual
import com.hopcape.odo.feature.garage.resources.gr_ah_manual_sub
import com.hopcape.odo.feature.garage.resources.gr_ah_scan
import com.hopcape.odo.feature.garage.resources.gr_ah_scan_sub
import com.hopcape.odo.feature.garage.resources.gr_ah_subtitle
import com.hopcape.odo.feature.garage.resources.gr_ah_title
import com.hopcape.odo.feature.garage.resources.gr_ah_view
import com.hopcape.odo.feature.garage.resources.gr_ah_view_sub
import com.hopcape.odo.feature.garage.resources.gr_ah_verified
import org.jetbrains.compose.resources.stringResource

/**
 * "Add to service history" sheet ([com.hopcape.odo.core.navigation.OdoDestination.Garage.AddToHistory]):
 * scan (the verified fast path), manual entry, or add a document — plus the way out to
 * the full record.
 *
 * [onViewAll] opens the service-log feature's own list (through the shared
 * `OdoDestination.ServiceLog.List` key), which the garage's inline history — capped,
 * filtered, and summarised — can't stand in for.
 */
@Composable
internal fun AddToHistorySheetContent(
    onScan: () -> Unit,
    onManual: () -> Unit,
    onAddDocument: () -> Unit,
    onViewAll: () -> Unit,
) {
    com.hopcape.odo.feature.garage.presentation.GarageSheet {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.gr_ah_title), style = OdoTheme.typography.heading)
            OdoText(stringResource(Res.string.gr_ah_subtitle), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
        OptionCard(
            icon = IcCamera,
            title = stringResource(Res.string.gr_ah_scan),
            subtitle = stringResource(Res.string.gr_ah_scan_sub),
            onClick = onScan,
            highlighted = true,
            badge = stringResource(Res.string.gr_ah_verified),
        )
        OptionCard(IcPencil, stringResource(Res.string.gr_ah_manual), stringResource(Res.string.gr_ah_manual_sub), onManual)
        OptionCard(IcShieldCheck, stringResource(Res.string.gr_ah_doc), stringResource(Res.string.gr_ah_doc_sub), onAddDocument)
        OptionCard(IcJournal, stringResource(Res.string.gr_ah_view), stringResource(Res.string.gr_ah_view_sub), onViewAll)
    }
}

@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    badge: String? = null,
) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        onClick = onClick,
        color = if (highlighted) accent.copy(alpha = 0.08f) else OdoTheme.colors.surface,
        border = BorderStroke(1.dp, if (highlighted) accent.copy(alpha = 0.5f) else OdoTheme.colors.border),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint = if (highlighted) accent else OdoTheme.colors.text, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    // fill = false: the badge keeps its intrinsic width under a large
                    // font scale; the title wraps instead of squeezing the badge.
                    OdoText(title, style = OdoTheme.typography.heading, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) OdoBadge(badge, tone = OdoBadgeTone.Accent)
                }
                OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
    }
}
