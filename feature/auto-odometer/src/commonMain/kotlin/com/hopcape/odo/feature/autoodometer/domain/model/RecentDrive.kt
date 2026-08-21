package com.hopcape.odo.feature.autoodometer.domain.model

/**
 * One of the owner's last few drives, as the background-location step argues from it.
 *
 * The case for "all the time" is abstract until there is history. Once there is, it makes
 * itself: some drives were measured and some were not, and the ones that were not are the ones
 * where the app happened to be closed. That is the owner's own record, not a claim about what
 * might happen.
 *
 * [caught] is false for a `GAP_INFERRED` trip — the segment the car covered while nothing was
 * watching, which the engine reconstructs from the parked location rather than a live fix. It
 * has no honest distance to show, which is exactly the point being made.
 *
 * @param dayLabel the day as the owner would name it, already formatted ("12 Jun").
 * @param isToday whether [dayLabel] should give way to "Today" — a decision the screen makes,
 *   because the word is copy and copy belongs in the string table.
 */
internal data class RecentDrive(
    val dayLabel: String,
    val isToday: Boolean,
    val distanceKm: Int,
    val caught: Boolean,
)
