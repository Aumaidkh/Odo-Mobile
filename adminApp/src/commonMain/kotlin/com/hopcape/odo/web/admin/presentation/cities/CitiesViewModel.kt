package com.hopcape.odo.web.admin.presentation.cities

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.admin.domain.CitiesRepository
import com.hopcape.odo.web.admin.domain.City
import com.hopcape.odo.web.admin.domain.CitySubmission
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.presentation.readAll
import com.hopcape.odo.web.admin.presentation.readInto
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.ui.component.Page
import com.hopcape.odo.web.admin.resources.ad_cities_approved_done
import com.hopcape.odo.web.admin.resources.ad_cities_deleted_done
import com.hopcape.odo.web.admin.resources.ad_cities_duplicate
import com.hopcape.odo.web.admin.resources.ad_cities_rejected_done
import com.hopcape.odo.web.admin.resources.ad_cities_restored_done
import com.hopcape.odo.web.admin.resources.ad_cities_retired_done
import com.hopcape.odo.web.admin.resources.ad_cities_saved
import com.hopcape.odo.web.admin.resources.ad_cities_state_required
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

sealed interface CitiesEvent {
    data object Refresh : CitiesEvent
    data class SearchChanged(val value: String) : CitiesEvent
    data object RetiredVisibilityToggled : CitiesEvent
    data object NextPage : CitiesEvent
    data object PreviousPage : CitiesEvent

    data object AddRequested : CitiesEvent
    data class EditRequested(val city: City) : CitiesEvent
    data class ApproveRequested(val submission: CitySubmission) : CitiesEvent
    data object EditorDismissed : CitiesEvent
    data class EditorNameChanged(val value: String) : CitiesEvent
    data class EditorStateChanged(val value: String) : CitiesEvent
    data class EditorTierChanged(val value: Int) : CitiesEvent
    data object EditorSubmitted : CitiesEvent

    data class ActiveToggled(val city: City) : CitiesEvent
    data class SubmissionRejected(val submission: CitySubmission) : CitiesEvent
    data class SubmissionDeleted(val submission: CitySubmission) : CitiesEvent

    data object MessageDismissed : CitiesEvent
}

/**
 * What the editor is being used for.
 *
 * Approving is an edit, not a click, because `cities.state` is NOT NULL and the
 * app never asks an owner for one. The reviewer supplies it, and the same form
 * that adds a city is the right place to do that.
 */
@Immutable
sealed interface CityEditorMode {
    data object New : CityEditorMode
    data class Edit(val id: String) : CityEditorMode
    data class Approve(val submissionId: String) : CityEditorMode
}

@Immutable
data class CityEditor(
    val mode: CityEditorMode,
    val name: FormField<String>,
    val state: FormField<String>,
    val tier: Int,
    /** Sits under the field it belongs to — a taken name, or a missing state. */
    val nameError: UiText? = null,
    val stateError: UiText? = null,
) {
    val canSubmit: Boolean get() = name.value.isNotBlank() && state.value.isNotBlank()
}

@Immutable
data class CitiesUiState(
    val catalog: Loadable<List<City>> = Loadable.Loading,
    val submissions: Loadable<List<CitySubmission>> = Loadable.Loading,
    val search: String = "",
    val showRetired: Boolean = false,
    val page: Page = Page(0),
    val editor: CityEditor? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {

    /** Still waiting on somebody. Rejected rows stay in the table but not in the queue. */
    val pending: List<CitySubmission>
        get() = submissions.valueOrNull.orEmpty().filter { it.status == PENDING }

    /**
     * The catalog as the table draws it.
     *
     * Retired cities are hidden by default — there are a few hundred rows and the
     * retired ones are the least interesting — but they have to be reachable,
     * because restoring one is impossible if it cannot be listed.
     */
    /** Everything the filter admits, before the page window. */
    val matching: List<City>
        get() {
            val all = catalog.valueOrNull.orEmpty()
            val term = search.trim()
            return all
                .filter { showRetired || it.isActive }
                .filter {
                    term.isEmpty() ||
                        it.name.contains(term, ignoreCase = true) ||
                        it.state.contains(term, ignoreCase = true)
                }
        }

    /** The rows actually drawn. */
    val visible: List<City> get() = page.windowOf(matching)

    internal companion object {
        const val PENDING = "pending"
    }
}

/**
 * The cities catalog and its queue.
 *
 * Every write is followed by a re-read of both lists rather than a local edit of
 * the state. Approving a submission is the reason: the row moves from one table
 * to the other inside a database trigger, so the only client that knows what
 * happened afterwards is one that asks again.
 */
class CitiesViewModel(
    private val cities: CitiesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CitiesUiState())
    val state: StateFlow<CitiesUiState> = _state.asStateFlow()

    private val catalog = MutableStateFlow<Loadable<List<City>>>(Loadable.Loading)
    private val submissions = MutableStateFlow<Loadable<List<CitySubmission>>>(Loadable.Loading)

    init {
        viewModelScope.launch { catalog.collect { value -> _state.value = _state.value.copy(catalog = value) } }
        viewModelScope.launch { submissions.collect { value -> _state.value = _state.value.copy(submissions = value) } }
        load()
    }

    fun onEvent(event: CitiesEvent) {
        when (event) {
            CitiesEvent.Refresh -> load()
            // Both reset the window: a filter that matches nothing on page four
            // would otherwise look like an empty catalog.
            is CitiesEvent.SearchChanged ->
                _state.value = _state.value.copy(search = event.value, page = _state.value.page.reset())

            CitiesEvent.RetiredVisibilityToggled ->
                _state.value = _state.value.copy(
                    showRetired = !_state.value.showRetired,
                    page = _state.value.page.reset(),
                )

            CitiesEvent.NextPage -> if (_state.value.page.hasNext(_state.value.matching.size)) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index + 1))
            }

            CitiesEvent.PreviousPage -> if (_state.value.page.hasPrevious) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index - 1))
            }

            CitiesEvent.AddRequested -> _state.value = _state.value.copy(
                editor = CityEditor(CityEditorMode.New, textField(), textField(), DEFAULT_TIER),
            )

            is CitiesEvent.EditRequested -> _state.value = _state.value.copy(
                editor = CityEditor(
                    mode = CityEditorMode.Edit(event.city.id),
                    name = textField(event.city.name),
                    state = textField(event.city.state),
                    tier = event.city.tier,
                ),
            )

            is CitiesEvent.ApproveRequested -> _state.value = _state.value.copy(
                editor = CityEditor(
                    mode = CityEditorMode.Approve(event.submission.id),
                    name = textField(event.submission.name),
                    // Blank rather than absent: the reviewer has to type one, and a
                    // field that starts empty says so more plainly than a hint.
                    state = textField(event.submission.state.orEmpty()),
                    tier = event.submission.tier ?: DEFAULT_TIER,
                ),
            )

            CitiesEvent.EditorDismissed -> _state.value = _state.value.copy(editor = null)

            is CitiesEvent.EditorNameChanged -> editEditor {
                copy(name = name.update(event.value), nameError = null)
            }

            is CitiesEvent.EditorStateChanged -> editEditor {
                copy(state = state.update(event.value), stateError = null)
            }

            is CitiesEvent.EditorTierChanged -> editEditor { copy(tier = event.value) }

            CitiesEvent.EditorSubmitted -> submitEditor()

            is CitiesEvent.ActiveToggled -> write(
                done = if (event.city.isActive) Res.string.ad_cities_retired_done else Res.string.ad_cities_restored_done,
            ) { cities.setActive(event.city.id, !event.city.isActive) }

            is CitiesEvent.SubmissionRejected -> write(Res.string.ad_cities_rejected_done) {
                cities.decideSubmission(event.submission.id, accepted = false, state = null, tier = null)
            }

            is CitiesEvent.SubmissionDeleted -> write(Res.string.ad_cities_deleted_done) {
                cities.deleteSubmission(event.submission.id)
            }

            CitiesEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun load() = readAll(
        { busy -> _state.value = _state.value.copy(busy = busy) },
        { readInto(catalog) { cities.cities() } },
        { readInto(submissions) { cities.submissions() } },
    )

    private fun editEditor(block: CityEditor.() -> CityEditor) {
        _state.value = _state.value.copy(editor = _state.value.editor?.block())
    }

    /**
     * Validate, then write.
     *
     * The duplicate check is done here as well as by the unique index, and both
     * are worth having. The index is the truth — two reviewers can submit the same
     * name at the same moment — but catching it locally means the answer arrives
     * before the round trip and names the field rather than the request.
     */
    private fun submitEditor() {
        val editor = _state.value.editor ?: return
        if (!editor.canSubmit) {
            if (editor.state.value.isBlank()) {
                editEditor { copy(stateError = UiText.Resource(Res.string.ad_cities_state_required)) }
            }
            return
        }

        val name = editor.name.value.trim()
        val existingId = (editor.mode as? CityEditorMode.Edit)?.id
        val clash = _state.value.catalog.valueOrNull.orEmpty()
            .any { it.name.equals(name, ignoreCase = true) && it.id != existingId }
        if (clash) {
            editEditor { copy(nameError = UiText.Resource(Res.string.ad_cities_duplicate)) }
            return
        }

        val state = editor.state.value.trim()
        when (val mode = editor.mode) {
            CityEditorMode.New -> write(Res.string.ad_cities_saved, closeEditor = true) {
                cities.add(name, state, editor.tier)
            }

            is CityEditorMode.Edit -> write(Res.string.ad_cities_saved, closeEditor = true) {
                cities.edit(mode.id, name, state, editor.tier)
            }

            is CityEditorMode.Approve -> write(Res.string.ad_cities_approved_done, closeEditor = true) {
                cities.decideSubmission(mode.submissionId, accepted = true, state = state, tier = editor.tier)
            }
        }
    }

    /**
     * One write, then a re-read.
     *
     * A failure leaves the editor open with what was typed still in it. Closing it
     * on a failed save is how somebody loses a state they just looked up.
     */
    private fun write(
        done: org.jetbrains.compose.resources.StringResource,
        closeEditor: Boolean = false,
        action: suspend () -> Either<WebError, Unit>,
    ) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(busy = false, message = error.asUiText())
                    // The index caught what the local check could not — another
                    // reviewer got there first. Point at the field, not the toast.
                    if (error == WebError.Conflict) {
                        editEditor { copy(nameError = UiText.Resource(Res.string.ad_cities_duplicate)) }
                    }
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

    private companion object {
        /** What an unreviewed city is worth trusting, matching the promote trigger's default. */
        const val DEFAULT_TIER = 3
    }
}
