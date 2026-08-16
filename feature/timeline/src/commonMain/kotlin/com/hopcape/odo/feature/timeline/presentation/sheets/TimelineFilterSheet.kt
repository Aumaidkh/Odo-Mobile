package com.hopcape.odo.feature.timeline.presentation.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCheckbox
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoSwitchRow
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.timeline.domain.model.ActivityCategory
import com.hopcape.odo.feature.timeline.presentation.TimelineTestTags
import com.hopcape.odo.feature.timeline.resources.Res
import com.hopcape.odo.feature.timeline.resources.tl_filter_documents
import com.hopcape.odo.feature.timeline.resources.tl_filter_fuel
import com.hopcape.odo.feature.timeline.resources.tl_filter_flagged
import com.hopcape.odo.feature.timeline.resources.tl_filter_health
import com.hopcape.odo.feature.timeline.resources.tl_filter_milestones
import com.hopcape.odo.feature.timeline.resources.tl_filter_services
import com.hopcape.odo.feature.timeline.resources.tl_filter_show
import com.hopcape.odo.feature.timeline.resources.tl_filter_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The "show in timeline" filter sheet.
 *
 * State-free: every toggle goes straight to the shared filter store through [onCategory] /
 * [onOnlyFlagged], so the feed behind the sheet narrows as the owner ticks. The button only
 * dismisses — there is nothing left to apply by the time it is tapped.
 */
@Composable
internal fun TimelineFilterSheetContent(
    state: TimelineFilterUiState,
    onCategory: (ActivityCategory, Boolean) -> Unit,
    onOnlyFlagged: (Boolean) -> Unit,
    onShow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Four rows, a switch and a button are taller than the space a sheet gets on a
            // short screen, and a sheet is measured against what it was given — without this
            // the "Show N events" button sits below the display with no way to reach it.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.md)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(stringResource(Res.string.tl_filter_title), style = OdoTheme.typography.heading)

        ActivityCategory.entries.forEach { category ->
            FilterRow(
                label = stringResource(category.labelResource()),
                count = state.countOf(category),
                checked = category in state.filter.categories,
                testTag = TimelineTestTags.filterRow(category),
                onCheckedChange = { onCategory(category, it) },
            )
        }

        OdoDivider()
        OdoSwitchRow(
            label = stringResource(Res.string.tl_filter_flagged),
            checked = state.filter.onlyFlagged,
            onCheckedChange = onOnlyFlagged,
            modifier = Modifier.testTag(TimelineTestTags.FILTER_ONLY_FLAGGED),
        )
        OdoButton(
            stringResource(Res.string.tl_filter_show, state.shownCount),
            onClick = onShow,
            modifier = Modifier.fillMaxWidth().testTag(TimelineTestTags.FILTER_APPLY),
        )
    }
}

private fun ActivityCategory.labelResource(): StringResource = when (this) {
    ActivityCategory.SERVICES -> Res.string.tl_filter_services
    ActivityCategory.FUEL -> Res.string.tl_filter_fuel
    ActivityCategory.DOCUMENTS -> Res.string.tl_filter_documents
    ActivityCategory.SCORE -> Res.string.tl_filter_health
    ActivityCategory.MILESTONES -> Res.string.tl_filter_milestones
}

@Composable
private fun FilterRow(
    label: String,
    count: Int,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.testTag(testTag),
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
