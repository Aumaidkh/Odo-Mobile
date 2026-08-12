package com.hopcape.odo.feature.timeline.presentation

import com.hopcape.odo.feature.timeline.domain.model.TimelineFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The filter the timeline is currently showing, shared between the tab and its sheet.
 *
 * The sheet is its own navigation destination with its own ViewModel, so the two cannot
 * hold the choice between them — one writes it and the other reads it here.
 *
 * In memory only, and reset when the process dies. The filter is a way of looking at the
 * feed rather than something about the car, and a filter that quietly survives a restart
 * looks exactly like history that has gone missing.
 *
 * Public because it is a lifetime, not just a class: the end-to-end suite runs every test in
 * one process, so a filter left on by one test would narrow the feed for all the rest. The
 * suite replaces this binding between tests, the way it replaces the entitlement port.
 */
class TimelineFilterStore {

    private val _filter = MutableStateFlow(TimelineFilter())
    internal val filter: StateFlow<TimelineFilter> = _filter.asStateFlow()

    internal fun update(transform: (TimelineFilter) -> TimelineFilter) = _filter.update(transform)
}
