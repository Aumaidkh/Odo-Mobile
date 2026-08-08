package com.hopcape.odo.core.triptracker.port

import com.hopcape.odo.core.triptracker.model.FixRequest
import com.hopcape.odo.core.triptracker.model.LocationSample
import kotlinx.coroutines.flow.Flow

internal interface LocationProvider {
    fun fixes(spec: FixRequest): Flow<LocationSample>
    suspend fun lastKnown(): LocationSample?
}
