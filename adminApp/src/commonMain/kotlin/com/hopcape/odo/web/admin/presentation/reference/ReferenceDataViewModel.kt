package com.hopcape.odo.web.admin.presentation.reference

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.admin.domain.Coverage
import com.hopcape.odo.web.admin.domain.JobPrice
import com.hopcape.odo.web.admin.domain.LabourRate
import com.hopcape.odo.web.admin.domain.PartPrice
import com.hopcape.odo.web.admin.domain.Provenance
import com.hopcape.odo.web.admin.domain.ReferenceDataRepository
import com.hopcape.odo.web.admin.domain.ResolvedBand
import com.hopcape.odo.web.admin.domain.ScheduleItem
import com.hopcape.odo.web.admin.domain.ServiceItem
import com.hopcape.odo.web.admin.domain.VehicleSegment
import com.hopcape.odo.web.admin.domain.WorkshopTier
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.presentation.readAll
import com.hopcape.odo.web.admin.presentation.readInto
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_ref_no_band
import com.hopcape.odo.web.admin.resources.ad_ref_saved
import com.hopcape.odo.web.admin.resources.ad_ref_status_done
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

sealed interface ReferenceEvent {
    data object Refresh : ReferenceEvent

    data class LabourEditRequested(val rate: LabourRate?) : ReferenceEvent
    data class JobEditRequested(val price: JobPrice?) : ReferenceEvent
    data class PartEditRequested(val price: PartPrice?) : ReferenceEvent
    data class ScheduleEditRequested(val item: ScheduleItem?) : ReferenceEvent

    data class EditorFieldChanged(val field: EditorField, val value: String) : ReferenceEvent
    data object EditorDismissed : ReferenceEvent
    data object EditorSubmitted : ReferenceEvent

    data class StatusToggled(val table: String, val id: String, val approved: Boolean) : ReferenceEvent

    data class PreviewFieldChanged(val field: PreviewField, val value: String) : ReferenceEvent
    data object PreviewRequested : ReferenceEvent

    data object MessageDismissed : ReferenceEvent
}

/** The editor's inputs, named so one event carries any of them. */
enum class EditorField {
    CityTier, WorkshopTier, RatePerHour,
    Category, Segment, FuelType, PartsRupees, LabourHours,
    PartSlug, Unit, MrpRupees,
    Brand, ItemSlug, DisplayName, DueKm, DueMonths,
    SourceUrl, SourceNote, VerifiedOn, Status,
}

enum class PreviewField { Category, City, Segment, WorkshopTier }

/** Which table the open editor writes to. */
enum class EditorKind { Labour, Job, Part, Schedule }

/**
 * One editor for four tables.
 *
 * The fields are held as strings because every one of them is typed, and a form
 * that parses on each keystroke cannot hold "12." on the way to "12.5".
 */
@Immutable
data class ReferenceEditor(
    val kind: EditorKind,
    val existingId: String?,
    val values: Map<EditorField, String> = emptyMap(),
    val error: UiText? = null,
) {
    operator fun get(field: EditorField): String = values[field].orEmpty()

    fun with(field: EditorField, value: String) = copy(values = values + (field to value), error = null)

    /** Enough typed to attempt a save. The database holds the real constraints. */
    val canSubmit: Boolean
        get() = when (kind) {
            EditorKind.Labour -> this[EditorField.RatePerHour].toLongOrNull()?.let { it > 0 } == true
            EditorKind.Job -> this[EditorField.Category].isNotBlank() &&
                this[EditorField.LabourHours].toDoubleOrNull()?.let { it > 0 } == true
            EditorKind.Part -> this[EditorField.PartSlug].isNotBlank() &&
                this[EditorField.MrpRupees].toLongOrNull()?.let { it > 0 } == true
            EditorKind.Schedule -> this[EditorField.ItemSlug].isNotBlank() &&
                this[EditorField.DisplayName].isNotBlank() &&
                (this[EditorField.DueKm].isNotBlank() || this[EditorField.DueMonths].isNotBlank())
        }
}

/** The resolve preview: what the app would answer for one lookup. */
@Immutable
data class PreviewState(
    val category: String = "",
    val city: String = "",
    val segment: VehicleSegment = VehicleSegment.Hatchback,
    val workshopTier: WorkshopTier = WorkshopTier.MultiBrand,
    val band: ResolvedBand? = null,
    val answered: Boolean = false,
    val running: Boolean = false,
) {
    val canRun: Boolean get() = category.isNotBlank() && city.isNotBlank()
}

@Immutable
data class ReferenceUiState(
    val coverage: Loadable<List<Coverage>> = Loadable.Loading,
    val labour: Loadable<List<LabourRate>> = Loadable.Loading,
    val jobs: Loadable<List<JobPrice>> = Loadable.Loading,
    val parts: Loadable<List<PartPrice>> = Loadable.Loading,
    val schedule: Loadable<List<ScheduleItem>> = Loadable.Loading,
    val categories: List<ServiceItem> = emptyList(),
    val editor: ReferenceEditor? = null,
    val preview: PreviewState = PreviewState(),
    val busy: Boolean = false,
    val message: UiText? = null,
)

/**
 * The reference data behind every price answer.
 *
 * Every write is followed by a re-read of the whole section rather than a local
 * edit. The coverage meter is derived in the database from the same rows the
 * tables draw, so a local patch would leave the meter disagreeing with the table
 * above it.
 */
class ReferenceDataViewModel(
    private val repository: ReferenceDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReferenceUiState())
    val state: StateFlow<ReferenceUiState> = _state.asStateFlow()

    private val coverage = MutableStateFlow<Loadable<List<Coverage>>>(Loadable.Loading)
    private val labour = MutableStateFlow<Loadable<List<LabourRate>>>(Loadable.Loading)
    private val jobs = MutableStateFlow<Loadable<List<JobPrice>>>(Loadable.Loading)
    private val parts = MutableStateFlow<Loadable<List<PartPrice>>>(Loadable.Loading)
    private val schedule = MutableStateFlow<Loadable<List<ScheduleItem>>>(Loadable.Loading)

    init {
        viewModelScope.launch { coverage.collect { v -> _state.value = _state.value.copy(coverage = v) } }
        viewModelScope.launch { labour.collect { v -> _state.value = _state.value.copy(labour = v) } }
        viewModelScope.launch { jobs.collect { v -> _state.value = _state.value.copy(jobs = v) } }
        viewModelScope.launch { parts.collect { v -> _state.value = _state.value.copy(parts = v) } }
        viewModelScope.launch { schedule.collect { v -> _state.value = _state.value.copy(schedule = v) } }
        load()
    }

    fun onEvent(event: ReferenceEvent) {
        when (event) {
            ReferenceEvent.Refresh -> load()

            is ReferenceEvent.LabourEditRequested -> _state.value = _state.value.copy(
                editor = labourEditor(event.rate),
            )

            is ReferenceEvent.JobEditRequested -> _state.value = _state.value.copy(
                editor = jobEditor(event.price),
            )

            is ReferenceEvent.PartEditRequested -> _state.value = _state.value.copy(
                editor = partEditor(event.price),
            )

            is ReferenceEvent.ScheduleEditRequested -> _state.value = _state.value.copy(
                editor = scheduleEditor(event.item),
            )

            is ReferenceEvent.EditorFieldChanged -> _state.value = _state.value.copy(
                editor = _state.value.editor?.with(event.field, event.value),
            )

            ReferenceEvent.EditorDismissed -> _state.value = _state.value.copy(editor = null)

            ReferenceEvent.EditorSubmitted -> submit()

            is ReferenceEvent.StatusToggled -> write(Res.string.ad_ref_status_done) {
                repository.setStatus(event.table, event.id, event.approved)
            }

            is ReferenceEvent.PreviewFieldChanged -> _state.value = _state.value.copy(
                preview = _state.value.preview.updated(event.field, event.value),
            )

            ReferenceEvent.PreviewRequested -> runPreview()

            ReferenceEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun load() {
        readAll(
            { busy -> _state.value = _state.value.copy(busy = busy) },
            { readInto(coverage) { repository.coverage() } },
            { readInto(labour) { repository.labourRates() } },
            { readInto(jobs) { repository.jobPrices() } },
            { readInto(parts) { repository.partPrices() } },
            { readInto(schedule) { repository.schedule() } },
        )
        // The category list is a picker's contents, not a panel. A failure here
        // leaves the picker empty rather than the section unreadable.
        viewModelScope.launch {
            repository.categories().onRight { items ->
                _state.value = _state.value.copy(categories = items)
            }
        }
    }

    private fun labourEditor(rate: LabourRate?) = ReferenceEditor(
        kind = EditorKind.Labour,
        existingId = rate?.let { "${it.cityTier}:${it.workshopTier.id}" },
        values = buildMap {
            put(EditorField.CityTier, rate?.cityTier?.toString() ?: "1")
            put(EditorField.WorkshopTier, rate?.workshopTier?.id ?: WorkshopTier.MultiBrand.id)
            put(EditorField.RatePerHour, rate?.paisePerHour?.let { (it / 100).toString() }.orEmpty())
            putAll(rate?.provenance.fields())
        },
    )

    private fun jobEditor(price: JobPrice?) = ReferenceEditor(
        kind = EditorKind.Job,
        existingId = price?.id,
        values = buildMap {
            put(EditorField.Category, price?.categoryId ?: _state.value.categories.firstOrNull()?.id.orEmpty())
            put(EditorField.Segment, price?.segment?.id ?: VehicleSegment.Hatchback.id)
            put(EditorField.FuelType, price?.fuelType.orEmpty())
            put(EditorField.PartsRupees, price?.partsPaise?.let { (it / 100).toString() } ?: "0")
            put(EditorField.LabourHours, price?.labourHours?.toString().orEmpty())
            putAll(price?.provenance.fields())
        },
    )

    private fun partEditor(price: PartPrice?) = ReferenceEditor(
        kind = EditorKind.Part,
        existingId = price?.id,
        values = buildMap {
            put(EditorField.PartSlug, price?.partSlug.orEmpty())
            put(EditorField.Segment, price?.segment?.id.orEmpty())
            put(EditorField.FuelType, price?.fuelType.orEmpty())
            put(EditorField.Unit, price?.unit ?: "piece")
            put(EditorField.MrpRupees, price?.mrpPaise?.let { (it / 100).toString() }.orEmpty())
            putAll(price?.provenance.fields())
        },
    )

    private fun scheduleEditor(item: ScheduleItem?) = ReferenceEditor(
        kind = EditorKind.Schedule,
        existingId = item?.id,
        values = buildMap {
            put(EditorField.Brand, item?.brand.orEmpty())
            put(EditorField.ItemSlug, item?.itemSlug.orEmpty())
            put(EditorField.DisplayName, item?.displayName.orEmpty())
            put(EditorField.DueKm, item?.dueKm?.toString().orEmpty())
            put(EditorField.DueMonths, item?.dueMonths?.toString().orEmpty())
            putAll(item?.provenance.fields())
        },
    )

    private fun submit() {
        val editor = _state.value.editor ?: return
        if (!editor.canSubmit) return

        val provenance = Provenance(
            sourceUrl = editor[EditorField.SourceUrl].ifBlank { null },
            sourceNote = editor[EditorField.SourceNote].ifBlank { null },
            verifiedOn = editor[EditorField.VerifiedOn].ifBlank { null },
            status = editor[EditorField.Status].ifBlank { Provenance.DRAFT },
        )

        when (editor.kind) {
            EditorKind.Labour -> write(Res.string.ad_ref_saved, close = true) {
                repository.saveLabourRate(
                    LabourRate(
                        cityTier = editor[EditorField.CityTier].toIntOrNull() ?: 1,
                        workshopTier = WorkshopTier.ofId(editor[EditorField.WorkshopTier])
                            ?: WorkshopTier.MultiBrand,
                        paisePerHour = editor[EditorField.RatePerHour].toRupeesAsPaise(),
                        provenance = provenance,
                    ),
                )
            }

            EditorKind.Job -> write(Res.string.ad_ref_saved, close = true) {
                repository.saveJobPrice(
                    JobPrice(
                        id = editor.existingId.orEmpty(),
                        categoryId = editor[EditorField.Category],
                        categoryName = "",
                        segment = VehicleSegment.ofId(editor[EditorField.Segment])
                            ?: VehicleSegment.Hatchback,
                        fuelType = editor[EditorField.FuelType].ifBlank { null },
                        partsPaise = editor[EditorField.PartsRupees].toRupeesAsPaise(),
                        labourHours = editor[EditorField.LabourHours].toDoubleOrNull() ?: 0.0,
                        provenance = provenance,
                    ),
                )
            }

            EditorKind.Part -> write(Res.string.ad_ref_saved, close = true) {
                repository.savePartPrice(
                    PartPrice(
                        id = editor.existingId.orEmpty(),
                        partSlug = editor[EditorField.PartSlug].trim(),
                        segment = VehicleSegment.ofId(editor[EditorField.Segment]),
                        fuelType = editor[EditorField.FuelType].ifBlank { null },
                        unit = editor[EditorField.Unit],
                        mrpPaise = editor[EditorField.MrpRupees].toRupeesAsPaise(),
                        provenance = provenance,
                    ),
                )
            }

            EditorKind.Schedule -> write(Res.string.ad_ref_saved, close = true) {
                repository.saveScheduleItem(
                    ScheduleItem(
                        id = editor.existingId.orEmpty(),
                        brand = editor[EditorField.Brand].ifBlank { null },
                        itemSlug = editor[EditorField.ItemSlug].trim(),
                        displayName = editor[EditorField.DisplayName].trim(),
                        dueKm = editor[EditorField.DueKm].toIntOrNull(),
                        dueMonths = editor[EditorField.DueMonths].toIntOrNull(),
                        provenance = provenance,
                    ),
                )
            }
        }
    }

    /**
     * Ask the database what the app would answer.
     *
     * A miss is not a failure. The RPC returns no row when the category has no
     * approved job price, which is the deliberate silence for the categories no
     * segment average is honest about.
     */
    private fun runPreview() {
        val preview = _state.value.preview
        if (!preview.canRun || preview.running) return
        _state.value = _state.value.copy(preview = preview.copy(running = true))
        viewModelScope.launch {
            repository.resolve(
                categorySlug = preview.category,
                city = preview.city.trim(),
                segment = preview.segment,
                workshopTier = preview.workshopTier,
            ).fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(
                        preview = _state.value.preview.copy(running = false, band = null, answered = false),
                        message = error.asUiText(),
                    )
                },
                ifRight = { band ->
                    _state.value = _state.value.copy(
                        preview = _state.value.preview.copy(running = false, band = band, answered = true),
                        message = if (band == null) UiText.Resource(Res.string.ad_ref_no_band) else null,
                    )
                },
            )
        }
    }

    private fun write(
        done: StringResource,
        close: Boolean = false,
        action: suspend () -> Either<WebError, Unit>,
    ) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { error ->
                    // The editor stays open with what was typed still in it.
                    // Closing it on a failed save is how somebody loses a figure
                    // they just read off an estimator page.
                    _state.value = _state.value.copy(busy = false, message = error.asUiText())
                },
                ifRight = {
                    _state.value = _state.value.copy(
                        busy = false,
                        message = UiText.Resource(done),
                        editor = if (close) null else _state.value.editor,
                    )
                    load()
                },
            )
        }
    }
}

/** Provenance is typed the same way in all four editors. */
private fun Provenance?.fields(): Map<EditorField, String> = mapOf(
    EditorField.SourceUrl to this?.sourceUrl.orEmpty(),
    EditorField.SourceNote to this?.sourceNote.orEmpty(),
    EditorField.VerifiedOn to this?.verifiedOn.orEmpty(),
    EditorField.Status to (this?.status ?: Provenance.DRAFT),
)

/**
 * Rupees typed by a person into the paise the column holds.
 *
 * Whole rupees only. Every figure here is read off a price list where nothing is
 * quoted in paise, and accepting a decimal would invite a float into a money path.
 */
private fun String.toRupeesAsPaise(): Long = (trim().toLongOrNull() ?: 0L) * 100

private fun PreviewState.updated(field: PreviewField, value: String): PreviewState = when (field) {
    PreviewField.Category -> copy(category = value, band = null, answered = false)
    PreviewField.City -> copy(city = value, band = null, answered = false)
    PreviewField.Segment -> copy(
        segment = VehicleSegment.ofId(value) ?: segment,
        band = null,
        answered = false,
    )
    PreviewField.WorkshopTier -> copy(
        workshopTier = WorkshopTier.ofId(value) ?: workshopTier,
        band = null,
        answered = false,
    )
}

/** The coverage row for one table, or null when the view has not been read yet. */
fun ReferenceUiState.coverageOf(table: String): Coverage? =
    coverage.valueOrNull.orEmpty().firstOrNull { it.tableName == table }
