package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.City
import com.hopcape.odo.web.admin.domain.CitySubmission
import com.hopcape.odo.web.admin.presentation.cities.CitiesEvent
import com.hopcape.odo.web.admin.presentation.cities.CitiesUiState
import com.hopcape.odo.web.admin.presentation.cities.CityEditor
import com.hopcape.odo.web.admin.presentation.cities.CityEditorMode
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_cities_add
import com.hopcape.odo.web.admin.resources.ad_cities_approve
import com.hopcape.odo.web.admin.resources.ad_cities_approve_title
import com.hopcape.odo.web.admin.resources.ad_cities_cancel
import com.hopcape.odo.web.admin.resources.ad_cities_catalog_count
import com.hopcape.odo.web.admin.resources.ad_cities_catalog_title
import com.hopcape.odo.web.admin.resources.ad_cities_delete
import com.hopcape.odo.web.admin.resources.ad_cities_edit
import com.hopcape.odo.web.admin.resources.ad_cities_edit_title
import com.hopcape.odo.web.admin.resources.ad_cities_empty
import com.hopcape.odo.web.admin.resources.ad_cities_name
import com.hopcape.odo.web.admin.resources.ad_cities_queue_count
import com.hopcape.odo.web.admin.resources.ad_cities_queue_empty
import com.hopcape.odo.web.admin.resources.ad_cities_queue_title
import com.hopcape.odo.web.admin.resources.ad_cities_reject
import com.hopcape.odo.web.admin.resources.ad_cities_reported_on
import com.hopcape.odo.web.admin.resources.ad_cities_restore
import com.hopcape.odo.web.admin.resources.ad_cities_retire
import com.hopcape.odo.web.admin.resources.ad_cities_retired_badge
import com.hopcape.odo.web.admin.resources.ad_cities_save
import com.hopcape.odo.web.admin.resources.ad_cities_show_retired
import com.hopcape.odo.web.admin.resources.ad_cities_state
import com.hopcape.odo.web.admin.resources.ad_cities_tier
import com.hopcape.odo.web.admin.resources.ad_cities_tier_hint
import com.hopcape.odo.web.admin.resources.ad_cities_tier_value
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.resources.ad_users_showing
import com.hopcape.odo.web.admin.ui.component.Pager
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.RowPanel
import com.hopcape.odo.web.admin.resources.ad_users_showing
import com.hopcape.odo.web.admin.ui.component.Pager
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.ReloadAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.component.TableHead
import com.hopcape.odo.web.admin.ui.component.TableRow
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

private val COLUMNS = listOf(2.2f, 1.4f, 0.7f, 1.4f)

/**
 * The queue above the catalog, on one page.
 *
 * Deliberately not two routes. A reviewer approving "Srinagar" needs to know
 * whether the catalog already has it under another spelling, and splitting the
 * URL would mean navigating away mid-decision to find out.
 */
@Composable
fun CitiesScreen(state: CitiesUiState, onEvent: (CitiesEvent) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_cities_queue_title)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Pill(stringResource(Res.string.ad_cities_queue_count, state.pending.size))
                            ReloadAction({ onEvent(CitiesEvent.Refresh) }, state.busy)
                        }
                    }
                    if (state.pending.isEmpty()) {
                        Muted(stringResource(Res.string.ad_cities_queue_empty))
                    } else {
                        state.pending.forEach { submission -> SubmissionRow(submission, state.busy, onEvent) }
                    }
                }
            }

            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_cities_catalog_title)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RetiredToggle(state.showRetired) { onEvent(CitiesEvent.RetiredVisibilityToggled) }
                            Pill(stringResource(Res.string.ad_cities_catalog_count, state.matching.size))
                            PrimaryAction(
                                stringResource(Res.string.ad_cities_add),
                                { onEvent(CitiesEvent.AddRequested) },
                                enabled = !state.busy,
                            )
                        }
                    }
                    TableHead(
                        listOf(
                            stringResource(Res.string.ad_cities_name).uppercase(),
                            stringResource(Res.string.ad_cities_state).uppercase(),
                            stringResource(Res.string.ad_cities_tier).uppercase(),
                            "",
                        ),
                        COLUMNS,
                    )
                }
            }

            if (state.visible.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_cities_empty)) } }
            } else {
                items(state.visible, key = { it.id }) { city -> CityRow(city, state.busy, onEvent) }
                item {
                    Pager(
                        page = state.page,
                        total = state.matching.size,
                        label = stringResource(
                            Res.string.ad_users_showing,
                            state.page.first(state.matching.size),
                            state.page.last(state.matching.size),
                            state.matching.size,
                        ),
                        onPrevious = { onEvent(CitiesEvent.PreviousPage) },
                        onNext = { onEvent(CitiesEvent.NextPage) },
                    )
                }
            }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(CitiesEvent.MessageDismissed) }
        }
    }

    state.editor?.let { EditorDialog(it, state.busy, onEvent) }
}

/** The retired filter, as a chip rather than a checkbox — Material's is 48dp tall. */
@Composable
private fun RetiredToggle(on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AdminTokens.field)
            .border(1.dp, if (on) AdminTokens.borderHover else AdminTokens.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(5.dp).clip(RoundedCornerShape(999.dp))
                .background(if (on) AdminTokens.accent else AdminTokens.textDim),
        )
        Text(
            stringResource(Res.string.ad_cities_show_retired),
            style = AdminType.strong,
            color = if (on) AdminTokens.textStrong else AdminTokens.textFaint,
        )
    }
}

@Composable
private fun SubmissionRow(submission: CitySubmission, busy: Boolean, onEvent: (CitiesEvent) -> Unit) {
    TableRow {
        Column(Modifier.weight(1f)) {
            CellPrimary(submission.name)
            CellSecondary(stringResource(Res.string.ad_cities_reported_on, submission.createdAt))
        }
        // Approve opens the editor rather than acting directly: `cities.state` is
        // NOT NULL and the app never asked the owner for one, so there is always a
        // field to fill before this can become a catalog row.
        RowAction(stringResource(Res.string.ad_cities_approve), { onEvent(CitiesEvent.ApproveRequested(submission)) }, !busy)
        RowAction(stringResource(Res.string.ad_cities_reject), { onEvent(CitiesEvent.SubmissionRejected(submission)) }, !busy)
        RowAction(
            stringResource(Res.string.ad_cities_delete),
            { onEvent(CitiesEvent.SubmissionDeleted(submission)) },
            !busy,
            color = AdminTokens.danger,
        )
    }
}

@Composable
private fun CityRow(city: City, busy: Boolean, onEvent: (CitiesEvent) -> Unit) {
    RowPanel {
        TableRow {
            Column(Modifier.weight(COLUMNS[0])) {
                CellPrimary(city.name, color = if (city.isActive) AdminTokens.text else AdminTokens.textFaint)
                if (!city.isActive) {
                    CellSecondary(stringResource(Res.string.ad_cities_retired_badge))
                }
            }
            Cell(city.state, Modifier.weight(COLUMNS[1]))
            Cell(city.tier.toString(), Modifier.weight(COLUMNS[2]))
            Row(
                modifier = Modifier.weight(COLUMNS[3]),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                RowAction(stringResource(Res.string.ad_cities_edit), { onEvent(CitiesEvent.EditRequested(city)) }, !busy)
                RowAction(
                    if (city.isActive) {
                        stringResource(Res.string.ad_cities_retire)
                    } else {
                        stringResource(Res.string.ad_cities_restore)
                    },
                    { onEvent(CitiesEvent.ActiveToggled(city)) },
                    !busy,
                )
            }
        }
    }
}

@Composable
private fun EditorDialog(editor: CityEditor, busy: Boolean, onEvent: (CitiesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(CitiesEvent.EditorDismissed) },
        containerColor = AdminTokens.card,
        titleContentColor = AdminTokens.text,
        textContentColor = AdminTokens.textStrong,
        title = {
            Text(
                when (editor.mode) {
                    CityEditorMode.New -> stringResource(Res.string.ad_cities_add)
                    is CityEditorMode.Edit -> stringResource(Res.string.ad_cities_edit_title)
                    is CityEditorMode.Approve -> stringResource(Res.string.ad_cities_approve_title)
                },
                style = AdminType.title,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    FieldLabel(stringResource(Res.string.ad_cities_name).uppercase())
                    AdminField(
                        editor.name.value,
                        { onEvent(CitiesEvent.EditorNameChanged(it)) },
                        stringResource(Res.string.ad_cities_name),
                        Modifier.fillMaxWidth(),
                    )
                    editor.nameError?.let { StatusText(it.resolve(), AdminTokens.danger, Modifier.padding(top = 4.dp)) }
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_cities_state).uppercase())
                    AdminField(
                        editor.state.value,
                        { onEvent(CitiesEvent.EditorStateChanged(it)) },
                        stringResource(Res.string.ad_cities_state),
                        Modifier.fillMaxWidth(),
                    )
                    editor.stateError?.let { StatusText(it.resolve(), AdminTokens.danger, Modifier.padding(top = 4.dp)) }
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_cities_tier).uppercase())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { tier ->
                            TierChip(tier, editor.tier == tier) { onEvent(CitiesEvent.EditorTierChanged(tier)) }
                        }
                    }
                    Text(
                        stringResource(Res.string.ad_cities_tier_hint),
                        style = AdminType.caption,
                        color = AdminTokens.textDim,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            PrimaryAction(
                if (editor.mode is CityEditorMode.Approve) {
                    stringResource(Res.string.ad_cities_approve)
                } else {
                    stringResource(Res.string.ad_cities_save)
                },
                { onEvent(CitiesEvent.EditorSubmitted) },
                enabled = editor.canSubmit && !busy,
            )
        },
        dismissButton = {
            RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(CitiesEvent.EditorDismissed) })
        },
    )
}

@Composable
private fun TierChip(tier: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) AdminTokens.text else AdminTokens.field)
            .border(1.dp, if (selected) AdminTokens.text else AdminTokens.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.ad_cities_tier_value, tier),
            style = AdminType.strong,
            color = if (selected) AdminTokens.canvas else AdminTokens.textStrong,
        )
    }
}
