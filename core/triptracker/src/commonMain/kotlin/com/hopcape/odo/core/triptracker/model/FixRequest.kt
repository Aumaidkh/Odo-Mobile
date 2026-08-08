package com.hopcape.odo.core.triptracker.model

import kotlin.time.Duration

/** How often a [com.hopcape.odo.core.triptracker.port.LocationProvider] should report fixes. */
internal data class FixRequest(val interval: Duration)
