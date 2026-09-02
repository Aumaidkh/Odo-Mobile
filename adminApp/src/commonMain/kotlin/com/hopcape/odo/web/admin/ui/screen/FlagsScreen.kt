package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.FeatureFlag
import com.hopcape.odo.web.admin.presentation.flags.FlagsEvent
import com.hopcape.odo.web.admin.presentation.flags.FlagsUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_cities_cancel
import com.hopcape.odo.web.admin.resources.ad_flags_col_action
import com.hopcape.odo.web.admin.resources.ad_flags_col_key
import com.hopcape.odo.web.admin.resources.ad_flags_col_value
import com.hopcape.odo.web.admin.resources.ad_flags_count
import com.hopcape.odo.web.admin.resources.ad_flags_edit
import com.hopcape.odo.web.admin.resources.ad_flags_empty
import com.hopcape.odo.web.admin.resources.ad_flags_note
import com.hopcape.odo.web.admin.resources.ad_flags_active
import com.hopcape.odo.web.admin.resources.ad_flags_col_state
import com.hopcape.odo.web.admin.resources.ad_flags_malformed
import com.hopcape.odo.web.admin.resources.ad_flags_off
import com.hopcape.odo.web.admin.resources.ad_flags_park
import com.hopcape.odo.web.admin.resources.ad_flags_parked
import com.hopcape.odo.web.admin.resources.ad_flags_restore
import com.hopcape.odo.web.admin.resources.ad_flags_on
import com.hopcape.odo.web.admin.resources.ad_flags_save
import com.hopcape.odo.web.admin.resources.ad_flags_title
import com.hopcape.odo.web.admin.resources.ad_flags_unconfigured_body
import com.hopcape.odo.web.admin.resources.ad_flags_unconfigured_title
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.RowPanel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.component.TableHead
import com.hopcape.odo.web.admin.ui.component.TableRow
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

private val COLUMNS = listOf(2.6f, 1.2f, 1.0f, 1.8f)

/**
 * The `app_config` table, which is the whole of remote configuration.
 *
 * Every row is an **override**, not the key itself: keys are declared in Kotlin with
 * `@Flag` and each ships a compiled default. Parking a row hands the key back to
 * that default, which is why Park is offered next to the value rather than hidden —
 * it is the safest way to undo a change, and safer than guessing the original.
 */
@Composable
fun FlagsScreen(state: FlagsUiState, onEvent: (FlagsEvent) -> Unit) {
    if (state.notConfigured) {
        NotConfigured()
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_flags_title)) {
                        Pill(stringResource(Res.string.ad_flags_count, state.visible.size))
                    }
                    Text(
                        stringResource(Res.string.ad_flags_note),
                        style = AdminType.caption,
                        color = AdminTokens.textDim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    TableHead(
                        listOf(
                            stringResource(Res.string.ad_flags_col_key),
                            stringResource(Res.string.ad_flags_col_value),
                            stringResource(Res.string.ad_flags_col_state),
                            stringResource(Res.string.ad_flags_col_action),
                        ),
                        COLUMNS,
                    )
                }
            }

            if (state.visible.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_flags_empty)) } }
            } else {
                items(state.visible, key = { it.key }) { flag -> FlagRow(flag, state, onEvent) }
            }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(FlagsEvent.MessageDismissed) }
        }
    }
}

@Composable
private fun FlagRow(flag: FeatureFlag, state: FlagsUiState, onEvent: (FlagsEvent) -> Unit) {
    val editing = state.editingKey == flag.key
    RowPanel {
        TableRow {
            Column(Modifier.weight(COLUMNS[0])) {
                CellPrimary(flag.key, color = if (flag.isActive) AdminTokens.text else AdminTokens.textFaint)
                if (flag.description.isNotBlank()) CellSecondary(flag.description)
                // A value that cannot parse resolves to the compiled default on the
                // device and says nothing about it, so it is said here.
                if (flag.isMalformed) {
                    StatusText(stringResource(Res.string.ad_flags_malformed, flag.valueType), AdminTokens.danger)
                }
            }

            Box(Modifier.weight(COLUMNS[1])) {
                when {
                    editing -> AdminField(
                        state.draft,
                        { onEvent(FlagsEvent.DraftChanged(it)) },
                        flag.valueType,
                        Modifier.width(200.dp),
                    )
                    // A boolean reads as a word, not as the string "true": the
                    // column is scanned down, and ON/OFF is what the eye picks up.
                    flag.isBoolean -> StatusText(
                        if (flag.isOn) stringResource(Res.string.ad_flags_on) else stringResource(Res.string.ad_flags_off),
                        if (flag.isOn) AdminTokens.text else AdminTokens.textDim,
                    )
                    else -> Cell(flag.value)
                }
            }

            // Whether the override applies at all, which is a different question
            // from what it is set to — a parked row still has a value.
            Box(Modifier.weight(COLUMNS[2])) {
                if (flag.isActive) {
                    StatusText(stringResource(Res.string.ad_flags_active), AdminTokens.text)
                } else {
                    StatusText(stringResource(Res.string.ad_flags_parked), AdminTokens.accent)
                }
            }

            Row(
                modifier = Modifier.weight(COLUMNS[3]),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (editing) {
                    PrimaryAction(stringResource(Res.string.ad_flags_save), { onEvent(FlagsEvent.DraftSaved) }, !state.busy)
                    RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(FlagsEvent.Editing(null)) })
                } else {
                    if (flag.isBoolean) {
                        RowAction(
                            if (flag.isOn) stringResource(Res.string.ad_flags_off) else stringResource(Res.string.ad_flags_on),
                            { onEvent(FlagsEvent.Toggled(flag)) },
                            !state.busy && flag.isActive,
                        )
                    } else {
                        RowAction(
                            stringResource(Res.string.ad_flags_edit),
                            { onEvent(FlagsEvent.Editing(flag.key)) },
                            !state.busy && flag.isActive,
                        )
                    }
                    RowAction(
                        if (flag.isActive) {
                            stringResource(Res.string.ad_flags_park)
                        } else {
                            stringResource(Res.string.ad_flags_restore)
                        },
                        { onEvent(FlagsEvent.ActiveToggled(flag)) },
                        !state.busy,
                        color = if (flag.isActive) AdminTokens.textFaint else AdminTokens.text,
                    )
                }
            }
        }
    }
}

/**
 * This build was never pointed at a backend.
 *
 * Its own screen rather than an error banner: a configuration answer, not a
 * failure, and there is nothing on the page a retry would help with.
 */
@Composable
private fun NotConfigured() {
    Column(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.ad_flags_unconfigured_title), style = AdminType.title, color = AdminTokens.text)
        Text(
            stringResource(Res.string.ad_flags_unconfigured_body),
            style = AdminType.body,
            color = AdminTokens.textFaint,
            modifier = Modifier.width(620.dp),
        )
    }
}
