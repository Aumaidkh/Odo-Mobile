package com.hopcape.odo.feature.timeline.presentation

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.feature.timeline.presentation.state.Loadable

/**
 * Timeline render state — the feed, plus the little the header needs to describe it.
 *
 * Nothing here is a pre-formatted display string, and nothing is stored that can be worked
 * out from the events themselves: month sections are a display grouping done in the
 * composable, the header line is built from string resources, and "new user" is derived. So
 * there is no copy baked into state and no second copy of the feed to keep in sync.
 */
@Immutable
internal data class TimelineUiState(
    val content: Loadable<TimelineContent> = Loadable.Loading,
    /** No active car — setup incomplete or the car was removed. A nudge, not a spinner:
     *  there is no read in flight, so a loader would spin forever. */
    val noCar: Boolean = false,
)

/** A loaded timeline. */
@Immutable
internal data class TimelineContent(
    /** The car the feed belongs to, e.g. "Swift VXI". */
    val carName: String = "",
    /** What the owner is looking at: newest first, after the filter. */
    val events: List<ActivityEvent> = emptyList(),
    /**
     * How many events exist before filtering. The header counts the car's whole record, so
     * unticking a category narrows the feed without rewriting its history.
     */
    val totalEvents: Int = 0,
    /** True when the filter is hiding something — the header says so rather than lying. */
    val isFiltered: Boolean = false,
) {
    /**
     * Nothing logged yet (at most the "car added" milestone) — shows the "build your car's
     * story" call to action instead of a populated feed.
     *
     * Read against the unfiltered count, so a filter that hides everything is an empty
     * filter result rather than a car with no history.
     */
    val isNewUser: Boolean get() = !isFiltered && events.none { it !is ActivityEvent.CarAdded }

    /** The filter is on and has left nothing to show. */
    val isFilteredEmpty: Boolean get() = isFiltered && events.isEmpty()

    /** Earliest year on the feed — the "since 2020" in the header; null when empty. */
    val sinceYear: Int? get() = events.minOfOrNull { it.date.year }
}
