package com.hopcape.odo.feature.autoodometer.domain.model

import com.hopcape.odo.core.domain.shared.Distance

/**
 * The trip-logged screen's (M6) odometer numbers: what the drum reads now, and what it
 * read right before the trip being shown. The difference between the two is the "N km
 * added" line — kept as two absolute readings rather than a delta, since the drum
 * itself always shows an absolute number.
 */
internal data class DerivedOdometer(
    val current: Distance,
    val before: Distance,
)
