package com.hopcape.odo.web.admin.presentation.vehicles

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.admin.domain.VehicleMake
import com.hopcape.odo.web.admin.domain.VehicleModel
import com.hopcape.odo.web.admin.domain.VehicleSubmission
import com.hopcape.odo.web.admin.domain.VehiclesRepository
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.presentation.loadInto
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_vehicles_added
import com.hopcape.odo.web.admin.resources.ad_vehicles_approved_done
import com.hopcape.odo.web.admin.resources.ad_vehicles_deleted_done
import com.hopcape.odo.web.admin.resources.ad_vehicles_duplicate
import com.hopcape.odo.web.admin.resources.ad_vehicles_rejected_done
import com.hopcape.odo.web.admin.resources.ad_vehicles_saved
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.FormField
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.textField
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

sealed interface VehiclesEvent {
    data object Refresh : VehiclesEvent
    data class SearchChanged(val value: String) : VehiclesEvent
    data class MakeSelected(val makeId: String?) : VehiclesEvent

    data object AddRequested : VehiclesEvent
    data class RenameMakeRequested(val make: VehicleMake) : VehiclesEvent
    data class EditModelRequested(val model: VehicleModel) : VehiclesEvent
    data object EditorDismissed : VehiclesEvent
    data class EditorMakeChanged(val value: String) : VehiclesEvent
    data class EditorModelChanged(val value: String) : VehiclesEvent
    data class EditorVariantChanged(val value: String) : VehiclesEvent
    data object EditorSubmitted : VehiclesEvent

    /** Both steps of a delete: ask, then do. Nothing here removes a row on one click. */
    data class DeleteRequested(val target: DeleteTarget) : VehiclesEvent
    data object DeleteDismissed : VehiclesEvent
    data object DeleteConfirmed : VehiclesEvent

    data class SubmissionApproved(val submission: VehicleSubmission) : VehiclesEvent
    data class SubmissionRejected(val submission: VehicleSubmission) : VehiclesEvent
    data class SubmissionDeleted(val submission: VehicleSubmission) : VehiclesEvent

    data object MessageDismissed : VehiclesEvent
}

/**
 * What a delete would remove.
 *
 * Carries the count for a make, because deleting one cascades to every model
 * under it and "delete Tata" reads very differently from "delete Tata and its 47
 * models".
 */
@Immutable
sealed interface DeleteTarget {
    val label: String

    data class Make(val make: VehicleMake, val modelCount: Int) : DeleteTarget {
        override val label: String get() = make.name
    }

    data class Model(val model: VehicleModel) : DeleteTarget {
        override val label: String
            get() = model.variant?.let { "${model.name} $it" } ?: model.name
    }
}

@Immutable
sealed interface VehicleEditorMode {
    /** Adding an entry, which may create a make, a model and a trim at once. */
    data object New : VehicleEditorMode
    data class RenameMake(val id: String) : VehicleEditorMode
    data class EditModel(val id: String) : VehicleEditorMode
}

@Immutable
data class VehicleEditor(
    val mode: VehicleEditorMode,
    val make: FormField<String>,
    val model: FormField<String>,
    val variant: FormField<String>,
    val error: UiText? = null,
) {
    val canSubmit: Boolean
        get() = when (mode) {
            VehicleEditorMode.New -> make.value.isNotBlank() && model.value.isNotBlank()
            is VehicleEditorMode.RenameMake -> make.value.isNotBlank()
            is VehicleEditorMode.EditModel -> model.value.isNotBlank()
        }
}

/** A make with the models under it, as the list draws them. */
@Immutable
data class MakeGroup(val make: VehicleMake, val models: List<VehicleModel>)

@Immutable
data class VehiclesUiState(
    val makes: Loadable<List<VehicleMake>> = Loadable.Loading,
    val models: Loadable<List<VehicleModel>> = Loadable.Loading,
    val submissions: Loadable<List<VehicleSubmission>> = Loadable.Loading,
    val search: String = "",
    /** Null means every make. A filter, not navigation — the URL does not change. */
    val selectedMakeId: String? = null,
    val editor: VehicleEditor? = null,
    val pendingDelete: DeleteTarget? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {

    val pending: List<VehicleSubmission>
        get() = submissions.valueOrNull.orEmpty().filter { it.status == PENDING }

    val allMakes: List<VehicleMake> get() = makes.valueOrNull.orEmpty()

    /**
     * The catalog as the list draws it: makes, each with its matching models.
     *
     * A search term matches on the make as well as the model, so typing "tata"
     * shows everything Tata makes rather than nothing. A make whose own name
     * matches keeps all of its models; otherwise only the models that match
     * survive, and a make with none left drops out.
     */
    val groups: List<MakeGroup>
        get() {
            val term = search.trim()
            val byMake = models.valueOrNull.orEmpty().groupBy { it.makeId }
            return allMakes
                .filter { selectedMakeId == null || it.id == selectedMakeId }
                .mapNotNull { make ->
                    val all = byMake[make.id].orEmpty()
                    val matches = when {
                        term.isEmpty() -> all
                        make.name.contains(term, ignoreCase = true) -> all
                        else -> all.filter { model ->
                            model.name.contains(term, ignoreCase = true) ||
                                model.variant?.contains(term, ignoreCase = true) == true
                        }
                    }
                    // A make with no models left is not a result. A make that
                    // genuinely has none is, which is why the term is checked too:
                    // otherwise a newly added make would be invisible until it had
                    // a model, and unremovable because it could not be found.
                    when {
                        matches.isNotEmpty() -> MakeGroup(make, matches)
                        term.isEmpty() || make.name.contains(term, ignoreCase = true) -> MakeGroup(make, emptyList())
                        else -> null
                    }
                }
        }

    val modelCount: Int get() = groups.sumOf { it.models.size }

    internal companion object {
        const val PENDING = "pending"
    }
}

/**
 * The vehicle catalog and its queue.
 *
 * Every write is followed by a re-read of all three lists. Adding one entry can
 * create a make, a model and a trim inside one database function, and approving a
 * submission moves a row between tables inside a trigger — in both cases the only
 * client that knows the result is one that asks again.
 */
class VehiclesViewModel(
    private val vehicles: VehiclesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VehiclesUiState())
    val state: StateFlow<VehiclesUiState> = _state.asStateFlow()

    private val makes = MutableStateFlow<Loadable<List<VehicleMake>>>(Loadable.Loading)
    private val models = MutableStateFlow<Loadable<List<VehicleModel>>>(Loadable.Loading)
    private val submissions = MutableStateFlow<Loadable<List<VehicleSubmission>>>(Loadable.Loading)

    init {
        viewModelScope.launch { makes.collect { v -> _state.value = _state.value.copy(makes = v) } }
        viewModelScope.launch { models.collect { v -> _state.value = _state.value.copy(models = v) } }
        viewModelScope.launch { submissions.collect { v -> _state.value = _state.value.copy(submissions = v) } }
        load()
    }

    fun onEvent(event: VehiclesEvent) {
        when (event) {
            VehiclesEvent.Refresh -> load()
            is VehiclesEvent.SearchChanged -> _state.value = _state.value.copy(search = event.value)
            is VehiclesEvent.MakeSelected -> _state.value = _state.value.copy(selectedMakeId = event.makeId)

            VehiclesEvent.AddRequested -> _state.value = _state.value.copy(
                editor = VehicleEditor(VehicleEditorMode.New, textField(), textField(), textField()),
            )

            is VehiclesEvent.RenameMakeRequested -> _state.value = _state.value.copy(
                editor = VehicleEditor(
                    mode = VehicleEditorMode.RenameMake(event.make.id),
                    make = textField(event.make.name),
                    model = textField(),
                    variant = textField(),
                ),
            )

            is VehiclesEvent.EditModelRequested -> _state.value = _state.value.copy(
                editor = VehicleEditor(
                    mode = VehicleEditorMode.EditModel(event.model.id),
                    make = textField(),
                    model = textField(event.model.name),
                    variant = textField(event.model.variant.orEmpty()),
                ),
            )

            VehiclesEvent.EditorDismissed -> _state.value = _state.value.copy(editor = null)
            is VehiclesEvent.EditorMakeChanged -> editEditor { copy(make = make.update(event.value), error = null) }
            is VehiclesEvent.EditorModelChanged -> editEditor { copy(model = model.update(event.value), error = null) }
            is VehiclesEvent.EditorVariantChanged -> editEditor { copy(variant = variant.update(event.value), error = null) }
            VehiclesEvent.EditorSubmitted -> submitEditor()

            is VehiclesEvent.DeleteRequested -> _state.value = _state.value.copy(pendingDelete = event.target)
            VehiclesEvent.DeleteDismissed -> _state.value = _state.value.copy(pendingDelete = null)
            VehiclesEvent.DeleteConfirmed -> confirmDelete()

            is VehiclesEvent.SubmissionApproved -> write(Res.string.ad_vehicles_approved_done) {
                vehicles.decideSubmission(event.submission.id, accepted = true)
            }

            is VehiclesEvent.SubmissionRejected -> write(Res.string.ad_vehicles_rejected_done) {
                vehicles.decideSubmission(event.submission.id, accepted = false)
            }

            is VehiclesEvent.SubmissionDeleted -> write(Res.string.ad_vehicles_deleted_done) {
                vehicles.deleteSubmission(event.submission.id)
            }

            VehiclesEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun load() {
        loadInto(makes) { vehicles.makes() }
        loadInto(models) { vehicles.models() }
        loadInto(submissions) { vehicles.submissions() }
    }

    private fun editEditor(block: VehicleEditor.() -> VehicleEditor) {
        _state.value = _state.value.copy(editor = _state.value.editor?.block())
    }

    private fun submitEditor() {
        val editor = _state.value.editor ?: return
        if (!editor.canSubmit) return

        val make = editor.make.value.trim()
        val model = editor.model.value.trim()
        val variant = editor.variant.value.trim().ifBlank { null }

        when (val mode = editor.mode) {
            VehicleEditorMode.New -> {
                // The database would treat this as a no-op — every insert in
                // upsert_vehicle_catalog_entry is guarded by an existence check —
                // and report success. Saying so is better than a silent nothing.
                if (alreadyListed(make, model, variant)) {
                    editEditor { copy(error = UiText.Resource(Res.string.ad_vehicles_duplicate)) }
                    return
                }
                write(Res.string.ad_vehicles_added, closeEditor = true) { vehicles.add(make, model, variant) }
            }

            is VehicleEditorMode.RenameMake ->
                write(Res.string.ad_vehicles_saved, closeEditor = true) { vehicles.renameMake(mode.id, make) }

            is VehicleEditorMode.EditModel ->
                write(Res.string.ad_vehicles_saved, closeEditor = true) { vehicles.editModel(mode.id, model, variant) }
        }
    }

    /**
     * Whether this exact make/model/trim is already in the catalog.
     *
     * Case-insensitive, matching the database's own `lower(name)` comparison for
     * makes. A trim-less entry is a different row from a trim, so a null variant
     * only collides with another null variant.
     */
    private fun alreadyListed(make: String, model: String, variant: String?): Boolean {
        val makeId = _state.value.allMakes
            .firstOrNull { it.name.equals(make, ignoreCase = true) }
            ?.id
            ?: return false
        return _state.value.models.valueOrNull.orEmpty().any { row ->
            row.makeId == makeId &&
                row.name.equals(model, ignoreCase = true) &&
                (row.variant ?: "").equals(variant ?: "", ignoreCase = true)
        }
    }

    private fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.value = _state.value.copy(pendingDelete = null)
        write(Res.string.ad_vehicles_deleted_done) {
            when (target) {
                is DeleteTarget.Make -> vehicles.deleteMake(target.make.id)
                is DeleteTarget.Model -> vehicles.deleteModel(target.model.id)
            }
        }
    }

    private fun write(
        done: StringResource,
        closeEditor: Boolean = false,
        action: suspend () -> Either<WebError, Unit>,
    ) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(busy = false, message = error.asUiText())
                },
                ifRight = {
                    _state.value = _state.value.copy(
                        busy = false,
                        message = UiText.Resource(done),
                        editor = if (closeEditor) null else _state.value.editor,
                    )
                    load()
                },
            )
        }
    }
}
