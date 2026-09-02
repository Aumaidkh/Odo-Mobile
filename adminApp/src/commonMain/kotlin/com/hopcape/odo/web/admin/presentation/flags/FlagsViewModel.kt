package com.hopcape.odo.web.admin.presentation.flags

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.admin.domain.FeatureFlag
import com.hopcape.odo.web.admin.domain.FlagsRepository
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_flags_saved
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FlagsEvent {
    data object Refresh : FlagsEvent
    data class SearchChanged(val value: String) : FlagsEvent
    /** Boolean flags flip; everything else is edited as text. */
    data class Toggled(val flag: FeatureFlag) : FlagsEvent
    data class Editing(val key: String?) : FlagsEvent
    data class DraftChanged(val value: String) : FlagsEvent
    data object DraftSaved : FlagsEvent
    /** Parks an override, handing the key back to its compiled default. */
    data class ActiveToggled(val flag: FeatureFlag) : FlagsEvent
    data object MessageDismissed : FlagsEvent
}

@Immutable
data class FlagsUiState(
    val flags: Loadable<List<FeatureFlag>> = Loadable.Loading,
    val search: String = "",
    /** The key currently being edited as text, if any. */
    val editingKey: String? = null,
    val draft: String = "",
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val all: List<FeatureFlag>
        get() = (flags as? Loadable.Ready)?.value.orEmpty()

    val visible: List<FeatureFlag>
        get() {
            val term = search.trim()
            if (term.isEmpty()) return all
            return all.filter {
                it.key.contains(term, ignoreCase = true) || it.description.contains(term, ignoreCase = true)
            }
        }

    /** A backend this build was never pointed at, which is not a failure to retry. */
    val notConfigured: Boolean
        get() = (flags as? Loadable.Failed)?.reason == WebError.NotConfigured
}

/**
 * Feature flags, from the `app_config` table.
 *
 * No ETag and no compare-and-set: Remote Config replaced its whole template on
 * every write, so two people saving at once lost each other's changes unless the
 * tag caught it. A row update touches one row, and two people editing different
 * keys no longer collide at all.
 */
class FlagsViewModel(
    private val flags: FlagsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FlagsUiState())
    val state: StateFlow<FlagsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: FlagsEvent) {
        when (event) {
            FlagsEvent.Refresh -> load()
            is FlagsEvent.SearchChanged -> _state.value = _state.value.copy(search = event.value)

            is FlagsEvent.Toggled -> save(event.flag.key, (!event.flag.isOn).toString())

            is FlagsEvent.ActiveToggled -> write { flags.setActive(event.flag.key, !event.flag.isActive) }

            is FlagsEvent.Editing -> _state.value = _state.value.copy(
                editingKey = event.key,
                draft = event.key?.let { key -> _state.value.all.firstOrNull { it.key == key }?.value }.orEmpty(),
            )

            is FlagsEvent.DraftChanged -> _state.value = _state.value.copy(draft = event.value)

            FlagsEvent.DraftSaved -> {
                val key = _state.value.editingKey ?: return
                save(key, _state.value.draft)
            }

            FlagsEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun load() {
        _state.value = _state.value.copy(flags = Loadable.Loading)
        viewModelScope.launch {
            flags.flags().fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(
                        flags = Loadable.Failed(error.asUiText(), retryable = error != WebError.NotConfigured, reason = error),
                    )
                },
                ifRight = { rows -> _state.value = _state.value.copy(flags = Loadable.Ready(rows)) },
            )
        }
    }

    private fun save(key: String, value: String) = write { flags.set(key, value) }

    /**
     * Every write, and the re-read after it.
     *
     * Re-reading rather than patching the row in place: the database stamps
     * `updated_at` and `updated_by`, and a screen that guessed at those would show
     * a row that disagrees with the audit entry beside it.
     */
    private fun write(action: suspend () -> arrow.core.Either<WebError, Unit>) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { error -> _state.value = _state.value.copy(busy = false, message = error.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(
                        busy = false,
                        editingKey = null,
                        message = UiText.Resource(Res.string.ad_flags_saved),
                    )
                    load()
                },
            )
        }
    }
}
