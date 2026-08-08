package com.hopcape.odo.core.triptracker.port

import com.hopcape.odo.core.triptracker.model.MotionSignal
import kotlinx.coroutines.flow.Flow

internal interface MotionActivitySource {
    fun signals(): Flow<MotionSignal>
}
