package com.hopcape.odo.web.admin.presentation.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.admin.domain.DashboardRepository
import com.hopcape.odo.web.admin.domain.DashboardSnapshot
import com.hopcape.odo.web.admin.presentation.loadInto
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DashboardEvent {
    data object Refresh : DashboardEvent
}

@Immutable
data class DashboardUiState(
    val snapshot: Loadable<DashboardSnapshot> = Loadable.Loading,
) {
    val value: DashboardSnapshot? get() = snapshot.valueOrNull
}

/**
 * The landing screen: what needs a person, and how the fortnight is going.
 *
 * One read, because it is one `admin_dashboard()` call. A dashboard assembled from
 * a dozen separate reads settles in a visibly staggered way and shows a dozen
 * loading states, which is worse than waiting once for all of it.
 */
class DashboardViewModel(
    private val dashboard: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val snapshot = MutableStateFlow<Loadable<DashboardSnapshot>>(Loadable.Loading)

    init {
        viewModelScope.launch {
            snapshot.collect { value -> _state.value = _state.value.copy(snapshot = value) }
        }
        load()
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.Refresh -> load()
        }
    }

    private fun load() = loadInto(snapshot) { dashboard.snapshot() }
}
