package com.hopcape.odo.core.domain.trip.analysis

import com.hopcape.odo.core.domain.cost.analysis.TankMileage
import com.hopcape.odo.core.domain.cost.model.FuelFill
import kotlinx.datetime.LocalDate

/**
 * The Sunday recap. Each part is null when there is not enough data for it, and the recap
 * itself is null when every part is.
 */
data class WeeklySummary(val km: Int?, val perKmPaise: Long?, val distancePerUnit: Double?) {

    companion object {
        fun of(tripMeters: List<Long>, perKmPaise: Long?, distancePerUnit: Double?): WeeklySummary? {
            val km = tripMeters.takeIf { it.isNotEmpty() }?.let { ((it.sum() + 500) / 1000).toInt() }
            if (km == null && perKmPaise == null && distancePerUnit == null) return null
            return WeeklySummary(km, perKmPaise, distancePerUnit)
        }

        /** Mileage from tanks closed in [weekStart]..[weekEnd]; the fill before the week opens the first. */
        fun mileageFor(fillsNewestFirst: List<FuelFill>, weekStart: LocalDate, weekEnd: LocalDate): Double? {
            val inWeek = fillsNewestFirst.indices.filter { fillsNewestFirst[it].filledOn in weekStart..weekEnd }
            if (inWeek.isEmpty()) return null
            val window = fillsNewestFirst.subList(inWeek.first(), minOf(inWeek.last() + 2, fillsNewestFirst.size))
            return TankMileage.average(window)
        }
    }
}
