package com.hopcape.odo.core.domain.trip.analysis

/**
 * One day's drives for the evening notification. [fuelEstimatePaise] is km × the estimated
 * fuel rate, so it is always an estimate, and null when no fuel price is known.
 */
data class DailyDriveSummary(val drives: Int, val km: Int, val fuelEstimatePaise: Long?) {

    companion object {
        /** `null` on a day with no drives, which posts nothing. Rounds to the nearest km and rupee. */
        fun of(tripMeters: List<Long>, fuelRatePaisePerKm: Long?): DailyDriveSummary? {
            if (tripMeters.isEmpty()) return null
            val meters = tripMeters.sum()
            val estimate = fuelRatePaisePerKm?.let { rate -> ((rate * meters + 500) / 1000 + 50) / 100 * 100 }
            return DailyDriveSummary(tripMeters.size, ((meters + 500) / 1000).toInt(), estimate)
        }
    }
}
