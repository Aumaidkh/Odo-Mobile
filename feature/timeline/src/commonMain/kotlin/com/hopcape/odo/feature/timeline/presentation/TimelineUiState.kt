package com.hopcape.odo.feature.timeline.presentation

import com.hopcape.odo.feature.timeline.domain.model.TimelineEvent

/**
 * Timeline render state — the car, and its events newest-first.
 *
 * ViewModel-ready: **every** field has a default, so the first emission is simply
 * `MutableStateFlow(TimelineUiState())` — a loading feed with no car yet — and each
 * later emission is a `copy(...)` of whatever the aggregation use case delivered:
 *
 * ```
 * private val _state = MutableStateFlow(TimelineUiState())
 * val state = _state.asStateFlow()
 * … _state.update { it.copy(carName = car.name, events = events, isLoading = false) }
 * ```
 *
 * Nothing here is a pre-formatted display string, and nothing is stored that can be
 * worked out from the events themselves: month sections are a display grouping done
 * in the composable, the header line is built from string resources, and "new user"
 * is derived. So there is no copy baked into state and no second copy of the feed to
 * keep in sync.
 */
internal data class TimelineUiState(
    /** The car the feed belongs to, e.g. "Swift VXI". Blank until the car loads. */
    val carName: String = "",
    /** Newest first — [TimelineEvent.date] descending. */
    val events: List<TimelineEvent> = emptyList(),
    /** The initial load phase; `true` on the very first state, before any events arrive. */
    val isLoading: Boolean = true,
) {
    /**
     * Nothing logged yet (at most the "car added" milestone) — shows the
     * "build your car's story" call-to-action instead of a populated feed. Never true
     * while [isLoading], so the default state can't be mistaken for an empty one.
     */
    val isNewUser: Boolean get() = !isLoading && events.none { it !is TimelineEvent.CarAdded }

    /** Earliest year on the feed — the "since 2020" in the header; null when empty. */
    val sinceYear: Int? get() = events.minOfOrNull { it.date.year }
}
