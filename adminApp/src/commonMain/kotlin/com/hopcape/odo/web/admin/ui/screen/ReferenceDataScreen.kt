package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hopcape.odo.web.admin.domain.Coverage
import com.hopcape.odo.web.admin.domain.JobPrice
import com.hopcape.odo.web.admin.domain.LabourRate
import com.hopcape.odo.web.admin.domain.PartPrice
import com.hopcape.odo.web.admin.domain.Provenance
import com.hopcape.odo.web.admin.domain.ScheduleItem
import com.hopcape.odo.web.admin.domain.VehicleSegment
import com.hopcape.odo.web.admin.domain.WorkshopTier
import com.hopcape.odo.web.admin.presentation.reference.EditorField
import com.hopcape.odo.web.admin.presentation.reference.EditorKind
import com.hopcape.odo.web.admin.presentation.reference.PreviewField
import com.hopcape.odo.web.admin.presentation.reference.ReferenceEditor
import com.hopcape.odo.web.admin.presentation.reference.ReferenceEvent
import com.hopcape.odo.web.admin.presentation.reference.ReferenceUiState
import com.hopcape.odo.web.admin.presentation.reference.coverageOf
import com.hopcape.odo.web.core.presentation.state.resolve
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_cities_cancel
import com.hopcape.odo.web.admin.resources.ad_ref_add
import com.hopcape.odo.web.admin.resources.ad_ref_any
import com.hopcape.odo.web.admin.resources.ad_ref_band
import com.hopcape.odo.web.admin.resources.ad_ref_city_hint
import com.hopcape.odo.web.admin.resources.ad_ref_due_join
import com.hopcape.odo.web.admin.resources.ad_ref_due_km_value
import com.hopcape.odo.web.admin.resources.ad_ref_due_months_value
import com.hopcape.odo.web.admin.resources.ad_ref_f_brand
import com.hopcape.odo.web.admin.resources.ad_ref_f_category
import com.hopcape.odo.web.admin.resources.ad_ref_f_city
import com.hopcape.odo.web.admin.resources.ad_ref_f_city_tier
import com.hopcape.odo.web.admin.resources.ad_ref_f_display_name
import com.hopcape.odo.web.admin.resources.ad_ref_f_due_km
import com.hopcape.odo.web.admin.resources.ad_ref_f_due_months
import com.hopcape.odo.web.admin.resources.ad_ref_f_fuel
import com.hopcape.odo.web.admin.resources.ad_ref_f_hours
import com.hopcape.odo.web.admin.resources.ad_ref_f_item_slug
import com.hopcape.odo.web.admin.resources.ad_ref_f_mrp
import com.hopcape.odo.web.admin.resources.ad_ref_f_part_slug
import com.hopcape.odo.web.admin.resources.ad_ref_f_parts
import com.hopcape.odo.web.admin.resources.ad_ref_f_rate
import com.hopcape.odo.web.admin.resources.ad_ref_f_segment
import com.hopcape.odo.web.admin.resources.ad_ref_f_segment_any
import com.hopcape.odo.web.admin.resources.ad_ref_f_source_note
import com.hopcape.odo.web.admin.resources.ad_ref_f_source_url
import com.hopcape.odo.web.admin.resources.ad_ref_f_unit
import com.hopcape.odo.web.admin.resources.ad_ref_f_verified_on
import com.hopcape.odo.web.admin.resources.ad_ref_f_workshop
import com.hopcape.odo.web.admin.resources.ad_ref_f_workshop_tier
import com.hopcape.odo.web.admin.resources.ad_ref_labour_key
import com.hopcape.odo.web.admin.resources.ad_ref_mrp_value
import com.hopcape.odo.web.admin.resources.ad_ref_pair
import com.hopcape.odo.web.admin.resources.ad_ref_seg_hatchback
import com.hopcape.odo.web.admin.resources.ad_ref_seg_muv
import com.hopcape.odo.web.admin.resources.ad_ref_seg_sedan
import com.hopcape.odo.web.admin.resources.ad_ref_seg_suv
import com.hopcape.odo.web.admin.resources.ad_ref_tier_1
import com.hopcape.odo.web.admin.resources.ad_ref_tier_2
import com.hopcape.odo.web.admin.resources.ad_ref_tier_3
import com.hopcape.odo.web.admin.resources.ad_ref_unit_litre
import com.hopcape.odo.web.admin.resources.ad_ref_unit_piece
import com.hopcape.odo.web.admin.resources.ad_ref_unit_set
import com.hopcape.odo.web.admin.resources.ad_ref_wt_authorised
import com.hopcape.odo.web.admin.resources.ad_ref_wt_local
import com.hopcape.odo.web.admin.resources.ad_ref_wt_multi_brand
import com.hopcape.odo.web.admin.resources.ad_ref_approve
import com.hopcape.odo.web.admin.resources.ad_ref_col_action
import com.hopcape.odo.web.admin.resources.ad_ref_col_key
import com.hopcape.odo.web.admin.resources.ad_ref_col_source
import com.hopcape.odo.web.admin.resources.ad_ref_col_value
import com.hopcape.odo.web.admin.resources.ad_ref_coverage
import com.hopcape.odo.web.admin.resources.ad_ref_edit
import com.hopcape.odo.web.admin.resources.ad_ref_empty
import com.hopcape.odo.web.admin.resources.ad_ref_hours
import com.hopcape.odo.web.admin.resources.ad_ref_job_prices
import com.hopcape.odo.web.admin.resources.ad_ref_labour_rates
import com.hopcape.odo.web.admin.resources.ad_ref_modelled
import com.hopcape.odo.web.admin.resources.ad_ref_no_source
import com.hopcape.odo.web.admin.resources.ad_ref_observed
import com.hopcape.odo.web.admin.resources.ad_ref_part_prices
import com.hopcape.odo.web.admin.resources.ad_ref_per_hour
import com.hopcape.odo.web.admin.resources.ad_ref_preview
import com.hopcape.odo.web.admin.resources.ad_ref_preview_hint
import com.hopcape.odo.web.admin.resources.ad_ref_preview_run
import com.hopcape.odo.web.admin.resources.ad_ref_revert
import com.hopcape.odo.web.admin.resources.ad_ref_save
import com.hopcape.odo.web.admin.resources.ad_ref_schedule
import com.hopcape.odo.web.admin.resources.ad_ref_schedule_default
import com.hopcape.odo.web.admin.resources.ad_ref_title
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.LoadingPanel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.ReloadAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.component.RowPanel
import com.hopcape.odo.web.admin.ui.component.TableHead
import com.hopcape.odo.web.admin.ui.component.TableRow
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val COLUMNS = listOf(2.2f, 1.6f, 2f, 1.4f)

/**
 * The reference data behind every price answer.
 *
 * Four tables on one page rather than four pages. They are entered together off
 * the same estimator pages, and a job price is meaningless without the labour rate
 * it resolves through.
 */
@Composable
fun ReferenceDataScreen(state: ReferenceUiState, onEvent: (ReferenceEvent) -> Unit) {
    if (state.labour is Loadable.Loading) {
        LoadingPanel()
        return
    }
    (state.labour as? Loadable.Failed)?.let { failure ->
        LoadingPanel(
            message = failure.message.resolve(),
            onRetry = if (failure.retryable) ({ onEvent(ReferenceEvent.Refresh) }) else null,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { CoverageMeter(state, onEvent) }
            item { ResolvePreview(state, onEvent) }

            item {
                TablePanel(
                    title = stringResource(Res.string.ad_ref_labour_rates),
                    coverage = state.coverageOf(TABLE_LABOUR),
                    onAdd = { onEvent(ReferenceEvent.LabourEditRequested(null)) },
                )
            }
            val rates = state.labour.valueOrNull.orEmpty()
            if (rates.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_ref_empty)) } }
            } else {
                items(rates, key = { "${it.cityTier}:${it.workshopTier.id}" }) { rate ->
                    ReferenceRow(
                        key = stringResource(
                            Res.string.ad_ref_labour_key,
                            rate.cityTier,
                            stringResource(rate.workshopTier.labelResource()),
                        ),
                        value = stringResource(Res.string.ad_ref_per_hour, rate.paisePerHour / 100),
                        provenance = rate.provenance,
                        busy = state.busy,
                        onEdit = { onEvent(ReferenceEvent.LabourEditRequested(rate)) },
                        onStatus = {
                            onEvent(
                                ReferenceEvent.StatusToggled(
                                    TABLE_LABOUR,
                                    "${rate.cityTier}:${rate.workshopTier.id}",
                                    !rate.provenance.isApproved,
                                ),
                            )
                        },
                    )
                }
            }

            item {
                TablePanel(
                    title = stringResource(Res.string.ad_ref_job_prices),
                    coverage = state.coverageOf(TABLE_JOB),
                    onAdd = { onEvent(ReferenceEvent.JobEditRequested(null)) },
                )
            }
            val jobs = state.jobs.valueOrNull.orEmpty()
            if (jobs.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_ref_empty)) } }
            } else {
                items(jobs, key = { it.id }) { price ->
                    ReferenceRow(
                        key = stringResource(
                            Res.string.ad_ref_pair,
                            price.categoryName,
                            stringResource(price.segment.labelResource()),
                        ),
                        value = stringResource(
                            Res.string.ad_ref_hours,
                            price.partsPaise / 100,
                            price.labourHours.toString(),
                        ),
                        provenance = price.provenance,
                        busy = state.busy,
                        onEdit = { onEvent(ReferenceEvent.JobEditRequested(price)) },
                        onStatus = {
                            onEvent(
                                ReferenceEvent.StatusToggled(TABLE_JOB, price.id, !price.provenance.isApproved),
                            )
                        },
                    )
                }
            }

            item {
                TablePanel(
                    title = stringResource(Res.string.ad_ref_part_prices),
                    coverage = state.coverageOf(TABLE_PART),
                    onAdd = { onEvent(ReferenceEvent.PartEditRequested(null)) },
                )
            }
            val parts = state.parts.valueOrNull.orEmpty()
            if (parts.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_ref_empty)) } }
            } else {
                items(parts, key = { it.id }) { price ->
                    ReferenceRow(
                        key = stringResource(
                            Res.string.ad_ref_pair,
                            price.partSlug,
                            price.segment?.let { stringResource(it.labelResource()) }
                                ?: stringResource(Res.string.ad_ref_any),
                        ),
                        value = stringResource(Res.string.ad_ref_mrp_value, price.mrpPaise / 100, price.unit),
                        provenance = price.provenance,
                        busy = state.busy,
                        onEdit = { onEvent(ReferenceEvent.PartEditRequested(price)) },
                        onStatus = {
                            onEvent(
                                ReferenceEvent.StatusToggled(TABLE_PART, price.id, !price.provenance.isApproved),
                            )
                        },
                    )
                }
            }

            item {
                TablePanel(
                    title = stringResource(Res.string.ad_ref_schedule),
                    coverage = state.coverageOf(TABLE_SCHEDULE),
                    onAdd = { onEvent(ReferenceEvent.ScheduleEditRequested(null)) },
                )
            }
            val schedule = state.schedule.valueOrNull.orEmpty()
            if (schedule.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_ref_empty)) } }
            } else {
                items(schedule, key = { it.id }) { entry ->
                    ReferenceRow(
                        key = stringResource(
                            Res.string.ad_ref_pair,
                            entry.brand ?: stringResource(Res.string.ad_ref_schedule_default),
                            entry.displayName,
                        ),
                        value = entry.due(),
                        provenance = entry.provenance,
                        busy = state.busy,
                        onEdit = { onEvent(ReferenceEvent.ScheduleEditRequested(entry)) },
                        onStatus = {
                            onEvent(
                                ReferenceEvent.StatusToggled(
                                    TABLE_SCHEDULE,
                                    entry.id,
                                    !entry.provenance.isApproved,
                                ),
                            )
                        },
                    )
                }
            }
        }

        state.message?.let { Banner(it.resolve()) { onEvent(ReferenceEvent.MessageDismissed) } }
    }

    state.editor?.let { EditorDialog(it, state, onEvent) }
}

/**
 * Approved rows against the hand-entry budget.
 *
 * Counts approved rows only, because a draft is not servable. This is what says
 * when the bill check can be switched on.
 */
@Composable
private fun CoverageMeter(state: ReferenceUiState, onEvent: (ReferenceEvent) -> Unit) {
    Panel {
        PanelHeader(stringResource(Res.string.ad_ref_title)) {
            ReloadAction({ onEvent(ReferenceEvent.Refresh) }, state.busy)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val rows = state.coverage.valueOrNull.orEmpty()
            if (rows.isEmpty()) {
                Muted(stringResource(Res.string.ad_ref_empty))
            } else {
                rows.forEach { row ->
                    Pill(
                        stringResource(Res.string.ad_ref_coverage, row.tableName, row.approvedRows, row.expectedRows),
                        dot = if (row.isComplete) AdminTokens.accent else null,
                    )
                }
            }
        }
    }
}

/**
 * What the app would answer for one lookup, and which rung answered it.
 *
 * This is where a typo is caught. The alternative is finding out at a service
 * counter, from a user.
 */
@Composable
private fun ResolvePreview(state: ReferenceUiState, onEvent: (ReferenceEvent) -> Unit) {
    Panel {
        PanelHeader(stringResource(Res.string.ad_ref_preview))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(stringResource(Res.string.ad_ref_col_key))
                    Choice(
                        options = state.categories.map { it.slug to it.name },
                        selected = state.preview.category,
                        onSelect = { onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.Category, it)) },
                    )
                }
                Column(Modifier.width(160.dp)) {
                    FieldLabel(stringResource(Res.string.ad_ref_f_city))
                    AdminField(
                        value = state.preview.city,
                        onValueChange = { onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.City, it)) },
                        placeholder = stringResource(Res.string.ad_ref_city_hint),
                    )
                }
                Column(Modifier.width(150.dp)) {
                    FieldLabel(stringResource(Res.string.ad_ref_f_segment))
                    Choice(
                        options = segmentOptions(),
                        selected = state.preview.segment.id,
                        onSelect = { onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.Segment, it)) },
                    )
                }
                Column(Modifier.width(150.dp)) {
                    FieldLabel(stringResource(Res.string.ad_ref_f_workshop))
                    Choice(
                        options = workshopOptions(),
                        selected = state.preview.workshopTier.id,
                        onSelect = { onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.WorkshopTier, it)) },
                    )
                }
                PrimaryAction(
                    stringResource(Res.string.ad_ref_preview_run),
                    { onEvent(ReferenceEvent.PreviewRequested) },
                    state.preview.canRun && !state.preview.running,
                )
            }

            val band = state.preview.band
            when {
                band != null -> Column {
                    CellPrimary(stringResource(Res.string.ad_ref_band, band.p25 / 100, band.p75 / 100))
                    CellSecondary(
                        if (band.basis == "modelled") {
                            stringResource(Res.string.ad_ref_modelled, band.scope, band.avgPaise / 100)
                        } else {
                            stringResource(Res.string.ad_ref_observed, band.scope, band.sampleSize)
                        },
                    )
                }
                state.preview.answered -> CellSecondary(stringResource(Res.string.ad_ref_preview_hint))
                else -> Unit
            }
        }
    }
}

@Composable
private fun TablePanel(title: String, coverage: Coverage?, onAdd: () -> Unit) {
    Panel {
        PanelHeader(title) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                coverage?.let {
                    Pill(
                        "${it.approvedRows}/${it.expectedRows}",
                        dot = if (it.isComplete) AdminTokens.accent else null,
                    )
                }
                RowAction(stringResource(Res.string.ad_ref_add), onAdd)
            }
        }
        TableHead(
            listOf(
                stringResource(Res.string.ad_ref_col_key),
                stringResource(Res.string.ad_ref_col_value),
                stringResource(Res.string.ad_ref_col_source),
                stringResource(Res.string.ad_ref_col_action),
            ),
            COLUMNS,
        )
    }
}

/**
 * One row, whichever table it came from.
 *
 * The four tables differ in their columns and agree on everything that matters
 * here: a key, a figure, where it came from, and whether it is approved.
 */
@Composable
private fun ReferenceRow(
    key: String,
    value: String,
    provenance: Provenance,
    busy: Boolean,
    onEdit: () -> Unit,
    onStatus: () -> Unit,
) {
    RowPanel {
        TableRow {
            Column(Modifier.weight(COLUMNS[0])) {
                CellPrimary(key)
                provenance.sourceNote?.let { CellSecondary(it) }
            }
            Cell(value, Modifier.weight(COLUMNS[1]))
            Column(Modifier.weight(COLUMNS[2])) {
                CellSecondary(provenance.sourceUrl ?: stringResource(Res.string.ad_ref_no_source))
                provenance.verifiedOn?.let { CellSecondary(it) }
            }
            Row(
                modifier = Modifier.weight(COLUMNS[3]),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowAction(stringResource(Res.string.ad_ref_edit), onEdit, !busy)
                RowAction(
                    if (provenance.isApproved) {
                        stringResource(Res.string.ad_ref_revert)
                    } else {
                        stringResource(Res.string.ad_ref_approve)
                    },
                    onStatus,
                    !busy,
                    color = if (provenance.isApproved) AdminTokens.textDim else AdminTokens.accent,
                )
            }
        }
    }
}

@Composable
private fun EditorDialog(editor: ReferenceEditor, state: ReferenceUiState, onEvent: (ReferenceEvent) -> Unit) {
    Dialog(onDismissRequest = { onEvent(ReferenceEvent.EditorDismissed) }) {
        Panel(Modifier.width(460.dp)) {
            PanelHeader(stringResource(Res.string.ad_ref_edit))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (editor.kind) {
                    EditorKind.Labour -> {
                        Labelled(stringResource(Res.string.ad_ref_f_city_tier)) {
                            Choice(
                                options = cityTierOptions(),
                                selected = editor[EditorField.CityTier],
                                onSelect = { onEvent(ReferenceEvent.EditorFieldChanged(EditorField.CityTier, it)) },
                            )
                        }
                        Labelled(stringResource(Res.string.ad_ref_f_workshop_tier)) {
                            Choice(
                                options = workshopOptions(),
                                selected = editor[EditorField.WorkshopTier],
                                onSelect = { onEvent(ReferenceEvent.EditorFieldChanged(EditorField.WorkshopTier, it)) },
                            )
                        }
                        Field(editor, EditorField.RatePerHour, stringResource(Res.string.ad_ref_f_rate), onEvent)
                    }

                    EditorKind.Job -> {
                        Labelled(stringResource(Res.string.ad_ref_f_category)) {
                            Choice(
                                options = state.categories.map { it.id to it.name },
                                selected = editor[EditorField.Category],
                                onSelect = { onEvent(ReferenceEvent.EditorFieldChanged(EditorField.Category, it)) },
                            )
                        }
                        Labelled(stringResource(Res.string.ad_ref_f_segment)) {
                            Choice(
                                options = segmentOptions(),
                                selected = editor[EditorField.Segment],
                                onSelect = { onEvent(ReferenceEvent.EditorFieldChanged(EditorField.Segment, it)) },
                            )
                        }
                        Field(editor, EditorField.FuelType, stringResource(Res.string.ad_ref_f_fuel), onEvent)
                        Field(editor, EditorField.PartsRupees, stringResource(Res.string.ad_ref_f_parts), onEvent)
                        Field(editor, EditorField.LabourHours, stringResource(Res.string.ad_ref_f_hours), onEvent)
                    }

                    EditorKind.Part -> {
                        Field(editor, EditorField.PartSlug, stringResource(Res.string.ad_ref_f_part_slug), onEvent)
                        Labelled(stringResource(Res.string.ad_ref_f_segment_any)) {
                            Choice(
                                options = listOf("" to stringResource(Res.string.ad_ref_any)) + segmentOptions(),
                                selected = editor[EditorField.Segment],
                                onSelect = { onEvent(ReferenceEvent.EditorFieldChanged(EditorField.Segment, it)) },
                            )
                        }
                        Labelled(stringResource(Res.string.ad_ref_f_unit)) {
                            Choice(
                                options = unitOptions(),
                                selected = editor[EditorField.Unit],
                                onSelect = { onEvent(ReferenceEvent.EditorFieldChanged(EditorField.Unit, it)) },
                            )
                        }
                        Field(editor, EditorField.MrpRupees, stringResource(Res.string.ad_ref_f_mrp), onEvent)
                    }

                    EditorKind.Schedule -> {
                        Field(editor, EditorField.Brand, stringResource(Res.string.ad_ref_f_brand), onEvent)
                        Field(editor, EditorField.ItemSlug, stringResource(Res.string.ad_ref_f_item_slug), onEvent)
                        Field(editor, EditorField.DisplayName, stringResource(Res.string.ad_ref_f_display_name), onEvent)
                        Field(editor, EditorField.DueKm, stringResource(Res.string.ad_ref_f_due_km), onEvent)
                        Field(editor, EditorField.DueMonths, stringResource(Res.string.ad_ref_f_due_months), onEvent)
                    }
                }

                // Every row carries its paperwork. A number with no source cannot
                // be re-checked in six months, and these go stale.
                Field(editor, EditorField.SourceUrl, stringResource(Res.string.ad_ref_f_source_url), onEvent)
                Field(editor, EditorField.SourceNote, stringResource(Res.string.ad_ref_f_source_note), onEvent)
                Field(editor, EditorField.VerifiedOn, stringResource(Res.string.ad_ref_f_verified_on), onEvent)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryAction(
                        stringResource(Res.string.ad_ref_save),
                        { onEvent(ReferenceEvent.EditorSubmitted) },
                        editor.canSubmit && !state.busy,
                    )
                    RowAction(
                        stringResource(Res.string.ad_cities_cancel),
                        { onEvent(ReferenceEvent.EditorDismissed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column {
        FieldLabel(label)
        content()
    }
}

@Composable
private fun Field(
    editor: ReferenceEditor,
    field: EditorField,
    label: String,
    onEvent: (ReferenceEvent) -> Unit,
) {
    Labelled(label) {
        AdminField(
            value = editor[field],
            onValueChange = { onEvent(ReferenceEvent.EditorFieldChanged(field, it)) },
            placeholder = label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A short list of options as a row of buttons.
 *
 * A dropdown for three values is a click to find out what the three values are.
 */
@Composable
private fun Choice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, label) ->
            RowAction(
                label = label,
                onClick = { onSelect(id) },
                color = if (id == selected) AdminTokens.accent else AdminTokens.textDim,
            )
        }
    }
}

/**
 * The resource for a name, picked **without composing**.
 *
 * These are read inside `items` loops. A `when` whose every arm is its own
 * `stringResource` call is several call sites at one position, and the resource
 * never settles into the slot it belongs to — the bug SectionLabels documents.
 * One `stringResource`, one slot, a changing argument.
 */
private fun VehicleSegment.labelResource(): StringResource = when (this) {
    VehicleSegment.Hatchback -> Res.string.ad_ref_seg_hatchback
    VehicleSegment.Sedan -> Res.string.ad_ref_seg_sedan
    VehicleSegment.Suv -> Res.string.ad_ref_seg_suv
    VehicleSegment.Muv -> Res.string.ad_ref_seg_muv
}

private fun WorkshopTier.labelResource(): StringResource = when (this) {
    WorkshopTier.Authorised -> Res.string.ad_ref_wt_authorised
    WorkshopTier.MultiBrand -> Res.string.ad_ref_wt_multi_brand
    WorkshopTier.Local -> Res.string.ad_ref_wt_local
}

/** Written out rather than mapped, so each label is its own call site. */
@Composable
private fun segmentOptions(): List<Pair<String, String>> = listOf(
    VehicleSegment.Hatchback.id to stringResource(Res.string.ad_ref_seg_hatchback),
    VehicleSegment.Sedan.id to stringResource(Res.string.ad_ref_seg_sedan),
    VehicleSegment.Suv.id to stringResource(Res.string.ad_ref_seg_suv),
    VehicleSegment.Muv.id to stringResource(Res.string.ad_ref_seg_muv),
)

@Composable
private fun workshopOptions(): List<Pair<String, String>> = listOf(
    WorkshopTier.Authorised.id to stringResource(Res.string.ad_ref_wt_authorised),
    WorkshopTier.MultiBrand.id to stringResource(Res.string.ad_ref_wt_multi_brand),
    WorkshopTier.Local.id to stringResource(Res.string.ad_ref_wt_local),
)

@Composable
private fun cityTierOptions(): List<Pair<String, String>> = listOf(
    "1" to stringResource(Res.string.ad_ref_tier_1),
    "2" to stringResource(Res.string.ad_ref_tier_2),
    "3" to stringResource(Res.string.ad_ref_tier_3),
)

@Composable
private fun unitOptions(): List<Pair<String, String>> = listOf(
    "litre" to stringResource(Res.string.ad_ref_unit_litre),
    "piece" to stringResource(Res.string.ad_ref_unit_piece),
    "set" to stringResource(Res.string.ad_ref_unit_set),
)

@Composable
private fun ScheduleItem.due(): String {
    val km = dueKm?.let { stringResource(Res.string.ad_ref_due_km_value, it) }
    val months = dueMonths?.let { stringResource(Res.string.ad_ref_due_months_value, it) }
    return when {
        km != null && months != null -> stringResource(Res.string.ad_ref_due_join, km, months)
        else -> km ?: months.orEmpty()
    }
}

private const val TABLE_LABOUR = "labour_rates"
private const val TABLE_JOB = "job_prices"
private const val TABLE_PART = "part_prices"
private const val TABLE_SCHEDULE = "service_schedule"
