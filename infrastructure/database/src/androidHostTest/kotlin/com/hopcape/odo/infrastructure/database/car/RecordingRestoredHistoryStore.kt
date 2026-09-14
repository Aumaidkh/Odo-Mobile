package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.history.RestoredHistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Remembers what the sync told it, so a test can ask whether the owner would be told. */
internal class RecordingRestoredHistoryStore : RestoredHistoryStore {

    val recorded = mutableListOf<CarId>()
    private val state = MutableStateFlow<CarId?>(null)

    override fun pending(): Flow<CarId?> = state

    override suspend fun record(carId: CarId) {
        recorded += carId
        state.value = carId
    }

    override suspend fun clear() {
        state.value = null
    }
}
