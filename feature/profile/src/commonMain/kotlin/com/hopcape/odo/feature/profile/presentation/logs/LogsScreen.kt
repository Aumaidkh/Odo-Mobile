package com.hopcape.odo.feature.profile.presentation.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hopcape.logging.api.LogLevel
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcMagnifier
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_back
import com.hopcape.odo.feature.profile.resources.pf_logs
import com.hopcape.odo.feature.profile.resources.pf_logs_empty
import com.hopcape.odo.feature.profile.resources.pf_logs_empty_filtered
import com.hopcape.odo.feature.profile.resources.pf_logs_search_hint
import com.hopcape.odo.feature.profile.resources.pf_logs_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * The current session's log file, read like Logcat: level chips, tag chips (built from
 * whatever tags have actually shown up), and a message search box — all three combine.
 *
 * Debug builds only, same reachability as [DeveloperOptionsScreen].
 */
@Composable
internal fun LogsScreen(
    state: LogsUiState,
    onSearchChanged: (String) -> Unit,
    onLevelToggled: (LogLevel) -> Unit,
    onTagToggled: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.pf_logs),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.pf_cd_back),
    ) { padding ->
        if (!state.available) {
            EmptyLogsMessage(
                stringResource(Res.string.pf_logs_unavailable),
                Modifier.padding(padding).padding(OdoTheme.spacing.md),
            )
            return@OdoScreen
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OdoInputField(
                value = state.searchText,
                onValueChange = onSearchChanged,
                placeholder = stringResource(Res.string.pf_logs_search_hint),
                leadingIcon = {
                    OdoIcon(IcMagnifier, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                items(LEVELS) { level ->
                    OdoChip(
                        label = level.name,
                        selected = level in state.selectedLevels,
                        onClick = { onLevelToggled(level) },
                    )
                }
            }

            if (state.allTags.isNotEmpty()) {
                Spacer(Modifier.height(OdoTheme.spacing.xs))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
                ) {
                    items(state.allTags) { tag ->
                        OdoChip(
                            label = tag,
                            selected = tag in state.selectedTags,
                            onClick = { onTagToggled(tag) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(OdoTheme.spacing.sm))
            OdoDivider()

            when {
                !state.hasAnyEntries -> EmptyLogsMessage(
                    stringResource(Res.string.pf_logs_empty),
                    Modifier.padding(OdoTheme.spacing.md),
                )
                state.visibleEntries.isEmpty() -> EmptyLogsMessage(
                    stringResource(Res.string.pf_logs_empty_filtered),
                    Modifier.padding(OdoTheme.spacing.md),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(OdoTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                ) {
                    items(state.visibleEntries) { entry -> LogLineRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun LogLineRow(entry: LogEntry) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(formatLogTime(entry.timestampMs), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
            OdoText(entry.level.name, style = OdoTheme.typography.caption, color = levelColor(entry.level))
            OdoText(entry.tag, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
        }
        OdoText(entry.message, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.text)
    }
}

@Composable
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.VERBOSE, LogLevel.DEBUG -> OdoTheme.colors.textMuted
    LogLevel.INFO -> OdoTheme.colors.accent
    LogLevel.WARN -> OdoTheme.colors.warning
    LogLevel.ERROR, LogLevel.FATAL -> OdoTheme.colors.danger
}

@Composable
private fun EmptyLogsMessage(text: String, modifier: Modifier = Modifier) {
    OdoText(text, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim, modifier = modifier)
}

private val LEVELS = LogLevel.entries
