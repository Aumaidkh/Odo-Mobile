package com.hopcape.odo.feature.timeline.presentation.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.feature.timeline.domain.model.ActivityCategory
import com.hopcape.odo.feature.timeline.domain.model.TimelineFilter
import com.hopcape.odo.feature.timeline.domain.usecase.ObserveTimelineUseCase
import com.hopcape.odo.feature.timeline.presentation.TimelineFilterStore
import com.hopcape.odo.feature.timeline.presentation.TimelineTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the "show in timeline" sheet.
 *
 * The sheet is its own navigation destination, so it reads and writes the same
 * [TimelineFilterStore] the tab observes — ticking a box narrows the feed underneath
 * immediately, and the button only dismisses.
 *
 * Its counts come from the same [ObserveTimelineUseCase] the tab uses, over the *unfiltered*
 * feed: a row saying "Documents 3" has to keep saying 3 while documents are hidden, or the
 * owner cannot tell what turning it back on would show.
 */
internal class TimelineFilterViewModel(
    activeCar: ActiveCarProvider,
    observeTimeline: ObserveTimelineUseCase,
    private val filters: TimelineFilterStore,
    private val telemetry: TimelineTelemetry,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TimelineFilterUiState> = activeCar.activeCarId
        .flatMapLatest { carId ->
            if (carId == null) {
                flowOf(TimelineFilterUiState())
            } else {
                combine(observeTimeline(carId), filters.filter) { snapshot, filter ->
                    TimelineFilterUiState(
                        filter = filter,
                        counts = ActivityCategory.entries.associateWith(snapshot::countOf),
                        shownCount = filter.apply(snapshot.events).size,
                    )
                }
            }
        }
        // A sheet that cannot read the counts still has to let the owner filter, so the
        // failure falls back to the counts it has rather than taking the scope down.
        .catch { cause ->
            telemetry.readFailed(cause)
            emit(TimelineFilterUiState(filter = filters.filter.value))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = TimelineFilterUiState(),
        )

    fun onCategoryToggled(category: ActivityCategory, selected: Boolean) {
        filters.update { it.withCategory(category, selected) }
        report()
    }

    fun onOnlyFlaggedToggled(onlyFlagged: Boolean) {
        filters.update { it.copy(onlyFlagged = onlyFlagged) }
        report()
    }

    /**
     * Reported from the store rather than from [state], which is one emission behind a tap
     * the moment it is made — an event describing the filter the owner just left would be
     * worse than none.
     */
    private fun report() {
        val filter = filters.filter.value
        telemetry.filterApplied(
            categories = filter.categories.map { it.name }.toSet(),
            onlyFlagged = filter.onlyFlagged,
        )
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** What the sheet renders: the current choice, and how much each row would show. */
internal data class TimelineFilterUiState(
    val filter: TimelineFilter = TimelineFilter(),
    val counts: Map<ActivityCategory, Int> = emptyMap(),
    val shownCount: Int = 0,
) {
    fun countOf(category: ActivityCategory): Int = counts[category] ?: 0
}
