package com.hopcape.odo.feature.costtracker.domain.model

/**
 * The window the running-cost figures are computed over — the screen's 3M / 6M / 1Y chips.
 *
 * [buckets] is how many bars the spend chart draws for the period. Six bars read well at
 * any width, so a year is shown two months at a time rather than as twelve thin columns;
 * shorter periods get one bar a month. [months] must divide evenly by [buckets], since a
 * bucket is a whole number of months.
 */
internal enum class CostPeriod(val months: Int, val buckets: Int) {
    M3(months = 3, buckets = 3),
    M6(months = 6, buckets = 6),
    Y1(months = 12, buckets = 6),
    ;

    /** How many months one bar covers. */
    val monthsPerBucket: Int get() = months / buckets
}
