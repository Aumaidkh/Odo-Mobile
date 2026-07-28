package com.hopcape.odo.feature.timeline.presentation.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import com.hopcape.odo.core.designsystem.component.OdoCheckbox
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.timeline.resources.Res
import com.hopcape.odo.feature.timeline.resources.tl_filter_documents
import com.hopcape.odo.feature.timeline.resources.tl_filter_flagged
import com.hopcape.odo.feature.timeline.resources.tl_filter_health
import com.hopcape.odo.feature.timeline.resources.tl_filter_milestones
import com.hopcape.odo.feature.timeline.resources.tl_filter_services
import com.hopcape.odo.feature.timeline.resources.tl_filter_show
import com.hopcape.odo.feature.timeline.resources.tl_filter_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Show in timeline" filter sheet ([com.hopcape.odo.core.navigation.OdoDestination.Timeline.Filter]).
 * UI-only: holds the toggles itself; [onShow] pops the sheet (applying the filter to the
 * feed lands with the ViewModel). The CTA counts the checked categories.
 */
@Composable
internal fun TimelineFilterSheetContent(onShow: () -> Unit) {
    var services by remember { mutableStateOf(true) }
    var documents by remember { mutableStateOf(true) }
    var health by remember { mutableStateOf(false) }
    var milestones by remember { mutableStateOf(false) }
    var onlyFlagged by remember { mutableStateOf(false) }

    val count = (if (services) 8 else 0) + (if (documents) 3 else 0) + (if (health) 2 else 0) + (if (milestones) 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.md)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(stringResource(Res.string.tl_filter_title), style = OdoTheme.typography.heading)
        FilterRow(stringResource(Res.string.tl_filter_services), 8, services) { services = it }
        FilterRow(stringResource(Res.string.tl_filter_documents), 3, documents) { documents = it }
        FilterRow(stringResource(Res.string.tl_filter_health), 2, health) { health = it }
        FilterRow(stringResource(Res.string.tl_filter_milestones), 1, milestones) { milestones = it }
        OdoDivider()
        OdoSwitchRow(
            label = stringResource(Res.string.tl_filter_flagged),
            checked = onlyFlagged,
            onCheckedChange = { onlyFlagged = it },
        )
        OdoButton(stringResource(Res.string.tl_filter_show, count), onClick = onShow, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FilterRow(label: String, count: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            OdoText(
                label,
                style = OdoTheme.typography.heading,
                color = if (checked) OdoTheme.colors.text else OdoTheme.colors.textDim,
                modifier = Modifier.weight(1f),
            )
            OdoText(count.toString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textMuted)
        }
    }
}
