package com.hopcape.odo.web.admin.presentation.audit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.admin.domain.AuditEntry
import com.hopcape.odo.web.admin.domain.AuditRepository
import com.hopcape.odo.web.admin.presentation.readAll
import com.hopcape.odo.web.admin.presentation.readInto
import com.hopcape.odo.web.admin.ui.component.Page
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuditEvent {
    data object Refresh : AuditEvent
    data class FilterChanged(val value: String) : AuditEvent
    data object NextPage : AuditEvent
    data object PreviousPage : AuditEvent
}

@Immutable
data class AuditUiState(
    val entries: Loadable<List<AuditEntry>> = Loadable.Loading,
    val filter: String = "",
    val page: Page = Page(0),
    /** A read is in flight. Read-only screen, so this is only ever a reload. */
    val busy: Boolean = false,
) {
    /**
     * Filtered here rather than in the query.
     *
     * The log is read a few hundred rows at a time and somebody scanning it is
     * usually narrowing by eye — "what happened to cities today". A round trip per
     * keystroke buys nothing at this size.
     */
    val matching: List<AuditEntry>
        get() {
            val term = filter.trim()
            if (term.isEmpty()) return entries.valueOrNull.orEmpty()
            return entries.valueOrNull.orEmpty().filter {
                it.subjectType.contains(term, ignoreCase = true) ||
                    it.action.contains(term, ignoreCase = true) ||
                    it.actorEmail?.contains(term, ignoreCase = true) == true
            }
        }

    val visible: List<AuditEntry> get() = page.windowOf(matching)
}

class AuditViewModel(
    private val audit: AuditRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuditUiState())
    val state: StateFlow<AuditUiState> = _state.asStateFlow()

    private val entries = MutableStateFlow<Loadable<List<AuditEntry>>>(Loadable.Loading)

    init {
        viewModelScope.launch { entries.collect { v -> _state.value = _state.value.copy(entries = v) } }
        load()
    }

    fun onEvent(event: AuditEvent) {
        when (event) {
            AuditEvent.Refresh -> load()
            is AuditEvent.FilterChanged ->
                _state.value = _state.value.copy(filter = event.value, page = _state.value.page.reset())

            AuditEvent.NextPage -> if (_state.value.page.hasNext(_state.value.matching.size)) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index + 1))
            }

            AuditEvent.PreviousPage -> if (_state.value.page.hasPrevious) {
                _state.value = _state.value.copy(page = _state.value.page.copy(index = _state.value.page.index - 1))
            }
        }
    }

    private fun load() = readAll(
        { busy -> _state.value = _state.value.copy(busy = busy) },
        { readInto(entries) { audit.recent() } },
    )
}
