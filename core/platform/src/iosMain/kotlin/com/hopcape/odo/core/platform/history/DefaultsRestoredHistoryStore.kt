package com.hopcape.odo.core.platform.history

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.history.RestoredHistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * [RestoredHistoryStore] on `NSUserDefaults` — the iOS mirror of
 * `PrefsRestoredHistoryStore`, with the same one-car-at-a-time rule.
 */
internal class DefaultsRestoredHistoryStore : RestoredHistoryStore {

    private val defaults = NSUserDefaults.standardUserDefaults
    private val state = MutableStateFlow(defaults.stringForKey(KEY_CAR_ID)?.let(::CarId))

    override fun pending(): Flow<CarId?> = state.asStateFlow()

    override suspend fun record(carId: CarId) {
        if (state.value == carId) return
        defaults.setObject(carId.value, KEY_CAR_ID)
        state.value = carId
    }

    override suspend fun clear() {
        defaults.removeObjectForKey(KEY_CAR_ID)
        state.value = null
    }

    private companion object {
        const val KEY_CAR_ID = "odo_restored_history_pending_car_id"
    }
}
