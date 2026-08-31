package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.hopcape.odo.web.admin.resources.ad_cities_search
import com.hopcape.odo.web.admin.resources.ad_cities_show_retired
import com.hopcape.odo.web.admin.resources.ad_cities_state
import com.hopcape.odo.web.admin.resources.ad_cities_tier
import com.hopcape.odo.web.admin.resources.ad_cities_tier_hint
import com.hopcape.odo.web.admin.resources.ad_cities_tier_value
import com.hopcape.odo.web.admin.resources.ad_dismiss
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

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
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Header(state, onEvent) }

            item {
                SectionHeading(
                    title = stringResource(Res.string.ad_cities_queue_title),
                    count = stringResource(Res.string.ad_cities_queue_count, state.pending.size),
                )
            }

            if (state.pending.isEmpty()) {
                item { Muted(stringResource(Res.string.ad_cities_queue_empty)) }
            } else {
                items(state.pending, key = { it.id }) { submission ->
                    SubmissionRow(submission, state.busy, onEvent)
                }
            }

            item {
                SectionHeading(
                    title = stringResource(Res.string.ad_cities_catalog_title),
                    count = stringResource(Res.string.ad_cities_catalog_count, state.visible.size),
                )
            }
            item { Filters(state, onEvent) }

            if (state.visible.isEmpty()) {
                item { Muted(stringResource(Res.string.ad_cities_empty)) }
            } else {
                items(state.visible, key = { it.id }) { city ->
                    CityRow(city, state.busy, onEvent)
                }
            }

            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(CitiesEvent.MessageDismissed) }
        }
    }

    state.editor?.let { editor ->
        EditorDialog(editor, state.busy, onEvent)
    }
}

@Composable
private fun Header(state: CitiesUiState, onEvent: (CitiesEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.ad_cities_catalog_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onEvent(CitiesEvent.AddRequested) }, enabled = !state.busy) {
            Text(stringResource(Res.string.ad_cities_add))
        }
    }
}

@Composable
private fun Filters(state: CitiesUiState, onEvent: (CitiesEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.search,
            onValueChange = { onEvent(CitiesEvent.SearchChanged(it)) },
            label = { Text(stringResource(Res.string.ad_cities_search)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.showRetired,
                onCheckedChange = { onEvent(CitiesEvent.RetiredVisibilityToggled) },
            )
            Text(stringResource(Res.string.ad_cities_show_retired), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SubmissionRow(submission: CitySubmission, busy: Boolean, onEvent: (CitiesEvent) -> Unit) {
    RowCard {
        Column(Modifier.weight(1f)) {
            Text(submission.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.ad_cities_reported_on, submission.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Approve opens the editor rather than acting directly: `cities.state` is
        // NOT NULL and the app never asked the owner for one, so there is always a
        // field to fill before this can become a catalog row.
        TextButton(onClick = { onEvent(CitiesEvent.ApproveRequested(submission)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_cities_approve))
        }
        TextButton(onClick = { onEvent(CitiesEvent.SubmissionRejected(submission)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_cities_reject))
        }
        TextButton(onClick = { onEvent(CitiesEvent.SubmissionDeleted(submission)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_cities_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CityRow(city: City, busy: Boolean, onEvent: (CitiesEvent) -> Unit) {
    RowCard {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    city.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (city.isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!city.isActive) {
                    Text(
                        stringResource(Res.string.ad_cities_retired_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                "${city.state} · ${stringResource(Res.string.ad_cities_tier)} ${city.tier}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onEvent(CitiesEvent.EditRequested(city)) }, enabled = !busy) {
            Text(stringResource(Res.string.ad_cities_edit))
        }
        TextButton(onClick = { onEvent(CitiesEvent.ActiveToggled(city)) }, enabled = !busy) {
            Text(
                if (city.isActive) {
                    stringResource(Res.string.ad_cities_retire)
                } else {
                    stringResource(Res.string.ad_cities_restore)
                },
            )
        }
    }
}

@Composable
private fun EditorDialog(editor: CityEditor, busy: Boolean, onEvent: (CitiesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(CitiesEvent.EditorDismissed) },
        title = {
            Text(
                when (editor.mode) {
                    CityEditorMode.New -> stringResource(Res.string.ad_cities_add)
                    is CityEditorMode.Edit -> stringResource(Res.string.ad_cities_edit_title)
                    is CityEditorMode.Approve -> stringResource(Res.string.ad_cities_approve_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.name.value,
                    onValueChange = { onEvent(CitiesEvent.EditorNameChanged(it)) },
                    label = { Text(stringResource(Res.string.ad_cities_name)) },
                    singleLine = true,
                    isError = editor.nameError != null,
                    supportingText = editor.nameError?.let { { Text(it.resolve()) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.state.value,
                    onValueChange = { onEvent(CitiesEvent.EditorStateChanged(it)) },
                    label = { Text(stringResource(Res.string.ad_cities_state)) },
                    singleLine = true,
                    isError = editor.stateError != null,
                    supportingText = editor.stateError?.let { { Text(it.resolve()) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.ad_cities_tier), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { tier ->
                        FilterChip(
                            selected = editor.tier == tier,
                            onClick = { onEvent(CitiesEvent.EditorTierChanged(tier)) },
                            label = { Text(stringResource(Res.string.ad_cities_tier_value, tier)) },
                        )
                    }
                }
                Text(
                    stringResource(Res.string.ad_cities_tier_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(CitiesEvent.EditorSubmitted) },
                enabled = editor.canSubmit && !busy,
            ) {
                Text(
                    if (editor.mode is CityEditorMode.Approve) {
                        stringResource(Res.string.ad_cities_approve)
                    } else {
                        stringResource(Res.string.ad_cities_save)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(CitiesEvent.EditorDismissed) }) {
                Text(stringResource(Res.string.ad_cities_cancel))
            }
        },
    )
}
