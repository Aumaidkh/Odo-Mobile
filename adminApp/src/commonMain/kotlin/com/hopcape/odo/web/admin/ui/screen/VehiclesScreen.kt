package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.VehicleModel
import com.hopcape.odo.web.admin.domain.VehicleSubmission
import com.hopcape.odo.web.admin.presentation.vehicles.DeleteTarget
import com.hopcape.odo.web.admin.presentation.vehicles.MakeGroup
import com.hopcape.odo.web.admin.presentation.vehicles.VehicleEditor
import com.hopcape.odo.web.admin.presentation.vehicles.VehicleEditorMode
import com.hopcape.odo.web.admin.presentation.vehicles.VehiclesEvent
import com.hopcape.odo.web.admin.presentation.vehicles.VehiclesUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_cities_cancel
import com.hopcape.odo.web.admin.resources.ad_vehicles_add
import com.hopcape.odo.web.admin.resources.ad_vehicles_add_hint
import com.hopcape.odo.web.admin.resources.ad_vehicles_all_makes
import com.hopcape.odo.web.admin.resources.ad_vehicles_approve
import com.hopcape.odo.web.admin.resources.ad_vehicles_base_row
import com.hopcape.odo.web.admin.resources.ad_vehicles_catalog_count
import com.hopcape.odo.web.admin.resources.ad_vehicles_catalog_title
import com.hopcape.odo.web.admin.resources.ad_vehicles_delete
import com.hopcape.odo.web.admin.resources.ad_vehicles_delete_base_warning
import com.hopcape.odo.web.admin.resources.ad_vehicles_delete_confirm
import com.hopcape.odo.web.admin.resources.ad_vehicles_delete_make_body
import com.hopcape.odo.web.admin.resources.ad_vehicles_delete_model_body
import com.hopcape.odo.web.admin.resources.ad_vehicles_delete_title
import com.hopcape.odo.web.admin.resources.ad_vehicles_edit
import com.hopcape.odo.web.admin.resources.ad_vehicles_edit_model
import com.hopcape.odo.web.admin.resources.ad_vehicles_empty
import com.hopcape.odo.web.admin.resources.ad_vehicles_make
import com.hopcape.odo.web.admin.resources.ad_vehicles_model
import com.hopcape.odo.web.admin.resources.ad_vehicles_no_models
import com.hopcape.odo.web.admin.resources.ad_vehicles_queue_count
import com.hopcape.odo.web.admin.resources.ad_vehicles_queue_empty
import com.hopcape.odo.web.admin.resources.ad_vehicles_queue_title
import com.hopcape.odo.web.admin.resources.ad_vehicles_reject
import com.hopcape.odo.web.admin.resources.ad_vehicles_rename
import com.hopcape.odo.web.admin.resources.ad_vehicles_rename_make
import com.hopcape.odo.web.admin.resources.ad_vehicles_reported_on
import com.hopcape.odo.web.admin.resources.ad_vehicles_save
import com.hopcape.odo.web.admin.resources.ad_vehicles_variant
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.resources.ad_vehicles_showing_makes
import com.hopcape.odo.web.admin.ui.component.Pager
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.resources.ad_vehicles_showing_makes
import com.hopcape.odo.web.admin.ui.component.Pager
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.ReloadAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.component.TableRow
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

/**
 * The queue above the catalog, grouped by make.
 *
 * The catalog is a few thousand rows, read once and filtered here rather than
 * re-queried per make: searching across every make is the thing a reviewer
 * actually does — checking whether a reported car is already listed under a
 * slightly different spelling — and a per-make request makes that impossible.
 *
 * The search box lives in the chrome. One box on a page is the design; two is a
 * question about which one applies.
 */
@Composable
fun VehiclesScreen(state: VehiclesUiState, onEvent: (VehiclesEvent) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_vehicles_queue_title)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Pill(stringResource(Res.string.ad_vehicles_queue_count, state.pending.size))
                            ReloadAction({ onEvent(VehiclesEvent.Refresh) }, state.busy)
                        }
                    }
                    if (state.pending.isEmpty()) {
                        Muted(stringResource(Res.string.ad_vehicles_queue_empty))
                    } else {
                        state.pending.forEach { SubmissionRow(it, state.busy, onEvent) }
                    }
                }
            }

            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_vehicles_catalog_title)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Pill(stringResource(Res.string.ad_vehicles_catalog_count, state.modelCount))
                            PrimaryAction(
                                stringResource(Res.string.ad_vehicles_add),
                                { onEvent(VehiclesEvent.AddRequested) },
                                enabled = !state.busy,
                            )
                        }
                    }
                    Column(Modifier.padding(16.dp)) { Makes(state, onEvent) }
                }
            }

            if (state.groups.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_vehicles_empty)) } }
            } else {
                item {
                    Pager(
                        page = state.page,
                        total = state.matchingGroups.size,
                        label = stringResource(
                            Res.string.ad_vehicles_showing_makes,
                            state.page.first(state.matchingGroups.size),
                            state.page.last(state.matchingGroups.size),
                            state.matchingGroups.size,
                        ),
                        onPrevious = { onEvent(VehiclesEvent.PreviousPage) },
                        onNext = { onEvent(VehiclesEvent.NextPage) },
                    )
                }
                state.groups.forEach { group ->
                    item(key = "make-${group.make.id}") {
                        Panel {
                            PanelHeader(group.make.name.uppercase()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RowAction(
                                        stringResource(Res.string.ad_vehicles_rename),
                                        { onEvent(VehiclesEvent.RenameMakeRequested(group.make)) },
                                        !state.busy,
                                    )
                                    RowAction(
                                        stringResource(Res.string.ad_vehicles_delete),
                                        {
                                            onEvent(
                                                VehiclesEvent.DeleteRequested(
                                                    DeleteTarget.Make(group.make, group.models.size),
                                                ),
                                            )
                                        },
                                        !state.busy,
                                        color = AdminTokens.danger,
                                    )
                                }
                            }
                            if (group.models.isEmpty()) {
                                Muted(stringResource(Res.string.ad_vehicles_no_models))
                            } else {
                                group.models.forEach { ModelRow(it, state.busy, onEvent) }
                            }
                        }
                    }
                }
            }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(VehiclesEvent.MessageDismissed) }
        }
    }

    state.editor?.let { EditorDialog(it, state.busy, onEvent) }
    state.pendingDelete?.let { DeleteDialog(it, state.busy, onEvent) }
}

/** A scrolling strip: dozens of makes, and a wrapping grid would fill the screen. */
@Composable
private fun Makes(state: VehiclesUiState, onEvent: (VehiclesEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MakeChip(stringResource(Res.string.ad_vehicles_all_makes), state.selectedMakeId == null) {
            onEvent(VehiclesEvent.MakeSelected(null))
        }
        state.allMakes.forEach { make ->
            MakeChip(make.name, state.selectedMakeId == make.id) {
                onEvent(VehiclesEvent.MakeSelected(make.id))
            }
        }
    }
}

@Composable
private fun MakeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AdminTokens.text else AdminTokens.field)
            .border(1.dp, if (selected) AdminTokens.text else AdminTokens.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = AdminType.strong,
            color = if (selected) AdminTokens.canvas else AdminTokens.textStrong,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubmissionRow(submission: VehicleSubmission, busy: Boolean, onEvent: (VehiclesEvent) -> Unit) {
    TableRow {
        Column(Modifier.weight(1f)) {
            CellPrimary(listOfNotNull(submission.make, submission.model, submission.variant).joinToString(" "))
            CellSecondary(stringResource(Res.string.ad_vehicles_reported_on, submission.createdAt))
        }
        // One click, unlike a city: a vehicle submission already carries a make and
        // a model, and the trim is genuinely optional, so there is nothing for a
        // reviewer to fill in first.
        RowAction(stringResource(Res.string.ad_vehicles_approve), { onEvent(VehiclesEvent.SubmissionApproved(submission)) }, !busy)
        RowAction(stringResource(Res.string.ad_vehicles_reject), { onEvent(VehiclesEvent.SubmissionRejected(submission)) }, !busy)
        RowAction(
            stringResource(Res.string.ad_vehicles_delete),
            { onEvent(VehiclesEvent.SubmissionDeleted(submission)) },
            !busy,
            color = AdminTokens.danger,
        )
    }
}

@Composable
private fun ModelRow(model: VehicleModel, busy: Boolean, onEvent: (VehiclesEvent) -> Unit) {
    TableRow {
        Column(Modifier.weight(2f)) {
            CellPrimary(model.name)
            // The trim-less row is labelled rather than left blank: an empty second
            // line reads like missing data, and this row is the one an owner picks
            // when they do not know their trim.
            CellSecondary(model.variant ?: stringResource(Res.string.ad_vehicles_base_row))
        }
        Cell(model.id, Modifier.weight(2f))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            RowAction(stringResource(Res.string.ad_vehicles_edit), { onEvent(VehiclesEvent.EditModelRequested(model)) }, !busy)
            RowAction(
                stringResource(Res.string.ad_vehicles_delete),
                { onEvent(VehiclesEvent.DeleteRequested(DeleteTarget.Model(model))) },
                !busy,
                color = AdminTokens.danger,
            )
        }
    }
}

@Composable
private fun EditorDialog(editor: VehicleEditor, busy: Boolean, onEvent: (VehiclesEvent) -> Unit) {
    val isNew = editor.mode == VehicleEditorMode.New
    AlertDialog(
        onDismissRequest = { onEvent(VehiclesEvent.EditorDismissed) },
        containerColor = AdminTokens.card,
        titleContentColor = AdminTokens.text,
        textContentColor = AdminTokens.textStrong,
        title = {
            Text(
                when (editor.mode) {
                    VehicleEditorMode.New -> stringResource(Res.string.ad_vehicles_add)
                    is VehicleEditorMode.RenameMake -> stringResource(Res.string.ad_vehicles_rename_make)
                    is VehicleEditorMode.EditModel -> stringResource(Res.string.ad_vehicles_edit_model)
                },
                style = AdminType.title,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editor.mode !is VehicleEditorMode.EditModel) {
                    Column {
                        FieldLabel(stringResource(Res.string.ad_vehicles_make).uppercase())
                        AdminField(
                            editor.make.value,
                            { onEvent(VehiclesEvent.EditorMakeChanged(it)) },
                            stringResource(Res.string.ad_vehicles_make),
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (editor.mode !is VehicleEditorMode.RenameMake) {
                    Column {
                        FieldLabel(stringResource(Res.string.ad_vehicles_model).uppercase())
                        AdminField(
                            editor.model.value,
                            { onEvent(VehiclesEvent.EditorModelChanged(it)) },
                            stringResource(Res.string.ad_vehicles_model),
                            Modifier.fillMaxWidth(),
                        )
                    }
                    Column {
                        FieldLabel(stringResource(Res.string.ad_vehicles_variant).uppercase())
                        AdminField(
                            editor.variant.value,
                            { onEvent(VehiclesEvent.EditorVariantChanged(it)) },
                            stringResource(Res.string.ad_vehicles_variant),
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                editor.error?.let { StatusText(it.resolve(), AdminTokens.danger) }
                if (isNew) {
                    Text(
                        stringResource(Res.string.ad_vehicles_add_hint),
                        style = AdminType.caption,
                        color = AdminTokens.textDim,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryAction(
                if (isNew) stringResource(Res.string.ad_vehicles_add) else stringResource(Res.string.ad_vehicles_save),
                { onEvent(VehiclesEvent.EditorSubmitted) },
                enabled = editor.canSubmit && !busy,
            )
        },
        dismissButton = {
            RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(VehiclesEvent.EditorDismissed) })
        },
    )
}

/**
 * Deleting asks first, and says what else goes with it.
 *
 * A make cascades to its models, which is the existing foreign key rather than a
 * choice made here — and "delete Tata" reads very differently from "delete Tata
 * and its 47 models".
 */
@Composable
private fun DeleteDialog(target: DeleteTarget, busy: Boolean, onEvent: (VehiclesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(VehiclesEvent.DeleteDismissed) },
        containerColor = AdminTokens.card,
        titleContentColor = AdminTokens.text,
        textContentColor = AdminTokens.textStrong,
        title = { Text(stringResource(Res.string.ad_vehicles_delete_title, target.label), style = AdminType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (target) {
                    is DeleteTarget.Make ->
                        Text(
                            stringResource(Res.string.ad_vehicles_delete_make_body, target.modelCount),
                            style = AdminType.body,
                            color = AdminTokens.textStrong,
                        )

                    is DeleteTarget.Model -> {
                        Text(
                            stringResource(Res.string.ad_vehicles_delete_model_body),
                            style = AdminType.body,
                            color = AdminTokens.textStrong,
                        )
                        if (target.model.isBaseRow) {
                            StatusText(
                                stringResource(Res.string.ad_vehicles_delete_base_warning),
                                AdminTokens.accent,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryAction(
                stringResource(Res.string.ad_vehicles_delete_confirm),
                { onEvent(VehiclesEvent.DeleteConfirmed) },
                enabled = !busy,
            )
        },
        dismissButton = {
            RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(VehiclesEvent.DeleteDismissed) })
        },
    )
}
