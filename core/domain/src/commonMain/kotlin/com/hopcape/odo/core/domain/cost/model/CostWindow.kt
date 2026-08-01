package com.hopcape.odo.core.domain.cost.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The stretch of days a running cost is computed over — both ends included.
 *
 * The cost tracker's period chips (3M / 6M / 1Y) are presentation; the dates behind them
 * are domain, because the same window decides which service logs count, which odometer
 * readings anchor the distance, and what the previous window is for the trend.
 */
data class CostWindow(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(start <= end) { "cost window ends before it starts: $start..$end" }
    }

    /** Days covered, counting both ends — a window of one day is 1, not 0. */
    val lengthInDays: Int get() = start.daysUntil(end) + 1

    operator fun contains(date: LocalDate): Boolean = date in start..end

    /**
     * The window of the same length immediately before this one — what the trend badge
     * compares against. The two never overlap: this one ends the day before [start].
     */
    fun previous(): CostWindow {
        val previousEnd = start.minus(1, DateTimeUnit.DAY)
        return CostWindow(
            start = previousEnd.minus(lengthInDays - 1, DateTimeUnit.DAY),
            end = previousEnd,
        )
    }

    companion object {
        /**
         * The [months]-long window ending on [day] — "the last 3 months", as the chips read.
         *
         * The start is the day *after* `day - months`, so that day belongs to the previous
         * window alone. Without the shift the boundary day would be counted twice and a
         * service logged on it would inflate both windows.
         */
        fun endingOn(day: LocalDate, months: Int): CostWindow {
            require(months > 0) { "a cost window spans at least one month, got $months" }
            return CostWindow(
                start = day.minus(months, DateTimeUnit.MONTH).plus(1, DateTimeUnit.DAY),
                end = day,
            )
        }
    }
}
