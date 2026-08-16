package com.hopcape.odo.feature.timeline.domain.model

import com.hopcape.odo.core.domain.activity.model.ActivityEvent

/** The groups the "Show in timeline" sheet offers, one per kind of event on the feed. */
internal enum class ActivityCategory {
    SERVICES,
    FUEL,
    DOCUMENTS,
    SCORE,
    MILESTONES,
}

/** Which group an event belongs to. */
internal val ActivityEvent.category: ActivityCategory
    get() = when (this) {
        is ActivityEvent.Service -> ActivityCategory.SERVICES
        is ActivityEvent.FuelFilled -> ActivityCategory.FUEL
        is ActivityEvent.DocumentFiled -> ActivityCategory.DOCUMENTS
        is ActivityEvent.ScoreChanged -> ActivityCategory.SCORE
        is ActivityEvent.CarAdded -> ActivityCategory.MILESTONES
    }

/** An entry the fairness check judged over the city average — what the switch narrows to. */
internal val ActivityEvent.isFlagged: Boolean
    get() = this is ActivityEvent.Service && overchargedBy != null

/**
 * What the owner has chosen to see on the timeline.
 *
 * Starts fully on: a filter is something the owner turns *off*, and a tab that opens
 * hiding events looks like missing history. Held in memory only — see
 * [TimelineFilterStore][com.hopcape.odo.feature.timeline.presentation.TimelineFilterStore].
 */
internal data class TimelineFilter(
    val categories: Set<ActivityCategory> = ActivityCategory.entries.toSet(),
    val onlyFlagged: Boolean = false,
) {
    /**
     * [events] narrowed to what is selected. The two controls combine: "only flagged"
     * narrows within the chosen categories rather than overriding them, so unticking
     * Services and asking for flagged entries correctly shows nothing.
     */
    fun apply(events: List<ActivityEvent>): List<ActivityEvent> =
        events.filter { it.category in categories && (!onlyFlagged || it.isFlagged) }

    /** Nothing is hidden — the state the tab opens in. */
    val hidesNothing: Boolean
        get() = !onlyFlagged && categories.containsAll(ActivityCategory.entries)

    fun withCategory(category: ActivityCategory, selected: Boolean): TimelineFilter =
        copy(categories = if (selected) categories + category else categories - category)
}
