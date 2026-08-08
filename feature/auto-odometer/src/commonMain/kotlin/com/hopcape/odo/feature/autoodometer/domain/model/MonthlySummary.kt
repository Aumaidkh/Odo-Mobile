package com.hopcape.odo.feature.autoodometer.domain.model

import com.hopcape.odo.core.domain.shared.Distance

/** Settings' (M7) "TRACKED THIS MONTH" card: total distance and trip count since the 1st. */
internal data class MonthlySummary(
    val totalDistance: Distance,
    val tripCount: Int,
)
