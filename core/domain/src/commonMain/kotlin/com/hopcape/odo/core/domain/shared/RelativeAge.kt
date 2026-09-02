package com.hopcape.odo.core.domain.shared

import kotlin.time.Instant

/**
 * How long ago something happened, coarsened to what a status line can honestly say —
 * "checked 2 hours ago" answers "can I trust this screen", not "when exactly".
 *
 * A value, not a string: each feature maps these to its own copy resources, which is what
 * keeps the wording localizable while the *granularity rule* (nothing finer than a
 * minute, nothing coarser than whole days) lives in one place.
 */
sealed interface RelativeAge {

    data object JustNow : RelativeAge

    data class Minutes(val count: Long) : RelativeAge

    data class Hours(val count: Long) : RelativeAge

    data class Days(val count: Long) : RelativeAge

    companion object {

        /** The age of [at] as seen from [now]. A future [at] (clock skew) reads as [JustNow]. */
        fun between(at: Instant, now: Instant): RelativeAge {
            val elapsed = now - at
            val minutes = elapsed.inWholeMinutes
            val hours = elapsed.inWholeHours
            val days = elapsed.inWholeDays
            return when {
                minutes < 1 -> JustNow
                minutes < 60 -> Minutes(minutes)
                hours < 24 -> Hours(hours)
                else -> Days(days)
            }
        }
    }
}
