package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
import com.hopcape.odo.web.admin.resources.ad_dismiss
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
import com.hopcape.odo.web.admin.resources.ad_vehicles_duplicate
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
import com.hopcape.odo.web.admin.resources.ad_vehicles_search
import com.hopcape.odo.web.admin.resources.ad_vehicles_title
import com.hopcape.odo.web.admin.resources.ad_vehicles_variant
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

/**
 * The queue above the catalog, grouped by make.
 *
 * The catalog is a few thousand rows, so it is read once and filtered here rather
 * than re-queried per make: searching across every make is the thing a reviewer
 * actually does — checking whether a reported car is already listed under a
 * slightly different spelling — and a per-make request makes that impossible.
 */
@Composable
fun VehiclesScreen(state: VehiclesUiState, onEvent: (VehiclesEvent) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Header(state, onEvent) }

            item {
                SectionHeading(
                    title = stringResource(Res.string.ad_vehicles_queue_title),
                    count = stringResource(Res.string.ad_vehicles_queue_count, state.pending.size),
                )
            }
            if (state.pending.isEmpty()) {
                item { Muted(stringResource(Res.string.ad_vehicles_queue_empty)) }
            } else {
                items(state.pending, key = { it.id }) { submission -> SubmissionRow(submission, state.busy, onEvent) }
            }

            item {
                SectionHeading(
                    title = stringResource(Res.string.ad_vehicles_catalog_title),
                    count = stringResource(Res.string.ad_vehicles_catalog_count, state.modelCount),
                )
            }
            item { Filters(state, onEvent) }

            if (state.groups.isEmpty()) {
                item { Muted(stringResource(Res.string.ad_vehicles_empty)) }
            } else {
                state.groups.forEach { group ->
                    item(key = "make-${group.make.id}") { MakeHeader(group, state.busy, onEvent) }
                    if (group.models.isEmpty()) {
                        item(key = "empty-${group.make.id}") {
                            Muted(stringResource(Res.string.ad_vehicles_no_models))
                        }
                    } else {
                        items(group.models, key = { it.id }) { model ->
                            ModelRow(model, state.busy, onEvent)
                        }
                    }
                }
            }

            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(VehiclesEvent.MessageDismissed) }
        }
    }

    state.editor?.let { EditorDialog(it, state.busy, onEvent) }
    state.pendingDelete?.let { DeleteDialog(it, state.busy, onEvent) }
}

@Composable
private fun Header(state: VehiclesUiState, onEvent: (VehiclesEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.ad_vehicles_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onEvent(VehiclesEvent.AddRequested) }, enabled = !state.busy) {
            Text(stringResource(Res.string.ad_vehicles_add))
        }
    }
}

@Composable
private fun Filters(state: VehiclesUiState, onEvent: (VehiclesEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = state.search,
            onValueChange = { onEvent(VehiclesEvent.SearchChanged(it)) },
            label = { Text(stringResource(Res.string.ad_vehicles_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A scrolling strip rather than a wrapping grid: there are dozens of makes
        // and a grid of them would push the catalog off the first screen.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.selectedMakeId == null,
                onClick = { onEvent(VehiclesEvent.MakeSelected(null)) },
                label = { Text(stringResource(Res.string.ad_vehicles_all_makes)) },
            )
            state.allMakes.forEach { make ->
                FilterChip(
                    selected = state.selectedMakeId == make.id,
                    onClick = { onEvent(VehiclesEvent.MakeSelected(make.id)) },
                    label = { Text(make.name) },
                )
            }
        }
    }
}

@Composable
private fun SubmissionRow(submission: VehicleSubmission, busy: Boolean, onEvent: (VehiclesEvent) -> Unit) {
    RowCard {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(submission.make, submission.model, submission.variant).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(Res.string.ad_vehicles_reported_on, submission.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // One click, unlike a city: a vehicle submission already carries a make
        // and a model, and the trim is genuinely optional, so there is nothing
        // for a reviewer to fill in first.
        TextButton(onClick = { onEvent(VehiclesEvent.SubmissionApproved(submission)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_vehicles_approve))
        }
        TextButton(onClick = { onEvent(VehiclesEvent.SubmissionRejected(submission)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_vehicles_reject))
        }
        TextButton(onClick = { onEvent(VehiclesEvent.SubmissionDeleted(submission)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_vehicles_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MakeHeader(group: MakeGroup, busy: Boolean, onEvent: (VehiclesEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.make.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onEvent(VehiclesEvent.RenameMakeRequested(group.make)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_vehicles_rename))
        }
        TextButton(
            onClick = {
                onEvent(VehiclesEvent.DeleteRequested(DeleteTarget.Make(group.make, group.models.size)))
            },
            enabled = !busy,
        ) {
            Text(stringResource(Res.string.ad_vehicles_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ModelRow(model: VehicleModel, busy: Boolean, onEvent: (VehiclesEvent) -> Unit) {
    RowCard {
        Column(Modifier.weight(1f)) {
            Text(model.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                // The trim-less row is labelled rather than left blank: an empty
                // second line reads like missing data, and this row is the one an
                // owner picks when they do not know their trim.
                model.variant ?: stringResource(Res.string.ad_vehicles_base_row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onEvent(VehiclesEvent.EditModelRequested(model)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_vehicles_edit))
        }
        TextButton(
            onClick = { onEvent(VehiclesEvent.DeleteRequested(DeleteTarget.Model(model))) },
            enabled = !busy,
        ) {
            Text(stringResource(Res.string.ad_vehicles_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EditorDialog(editor: VehicleEditor, busy: Boolean, onEvent: (VehiclesEvent) -> Unit) {
    val isNew = editor.mode == VehicleEditorMode.New
    AlertDialog(
        onDismissRequest = { onEvent(VehiclesEvent.EditorDismissed) },
        title = {
            Text(
                when (editor.mode) {
                    VehicleEditorMode.New -> stringResource(Res.string.ad_vehicles_add)
                    is VehicleEditorMode.RenameMake -> stringResource(Res.string.ad_vehicles_rename_make)
                    is VehicleEditorMode.EditModel -> stringResource(Res.string.ad_vehicles_edit_model)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editor.mode !is VehicleEditorMode.EditModel) {
                    OutlinedTextField(
                        value = editor.make.value,
                        onValueChange = { onEvent(VehiclesEvent.EditorMakeChanged(it)) },
                        label = { Text(stringResource(Res.string.ad_vehicles_make)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (editor.mode !is VehicleEditorMode.RenameMake) {
                    OutlinedTextField(
                        value = editor.model.value,
                        onValueChange = { onEvent(VehiclesEvent.EditorModelChanged(it)) },
                        label = { Text(stringResource(Res.string.ad_vehicles_model)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editor.variant.value,
                        onValueChange = { onEvent(VehiclesEvent.EditorVariantChanged(it)) },
                        label = { Text(stringResource(Res.string.ad_vehicles_variant)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                editor.error?.let {
                    Text(it.resolve(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (isNew) {
                    Text(
                        stringResource(Res.string.ad_vehicles_add_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(VehiclesEvent.EditorSubmitted) }, enabled = editor.canSubmit && !busy) {
                Text(
                    if (isNew) {
                        stringResource(Res.string.ad_vehicles_add)
                    } else {
                        stringResource(Res.string.ad_vehicles_save)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(VehiclesEvent.EditorDismissed) }) {
                Text(stringResource(Res.string.ad_dismiss))
            }
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
        title = { Text(stringResource(Res.string.ad_vehicles_delete_title, target.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (target) {
                    is DeleteTarget.Make ->
                        Text(stringResource(Res.string.ad_vehicles_delete_make_body, target.modelCount))

                    is DeleteTarget.Model -> {
                        Text(stringResource(Res.string.ad_vehicles_delete_model_body))
                        if (target.model.isBaseRow) {
                            Text(
                                stringResource(Res.string.ad_vehicles_delete_base_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(VehiclesEvent.DeleteConfirmed) }, enabled = !busy) {
                Text(stringResource(Res.string.ad_vehicles_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(VehiclesEvent.DeleteDismissed) }) {
                Text(stringResource(Res.string.ad_dismiss))
            }
        },
    )
}
