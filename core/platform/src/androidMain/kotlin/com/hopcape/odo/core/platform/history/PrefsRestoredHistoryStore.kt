package com.hopcape.odo.core.platform.history

import android.content.Context
import androidx.core.content.edit
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.history.RestoredHistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [RestoredHistoryStore] on `SharedPreferences`, mirrored into a flow the app shell reads.
 *
 * Prefs rather than the database, for the same reason as `PrefsShowcaseSeenStore`: this is
 * device state the owner's record is not made of, so it needs no table and no migration.
 *
 * The flow is what makes it usable from the shell — the sync writes this from a background
 * worker in the same process, and the sheet has to appear without anyone re-reading prefs.
 * One car at a time: the MVP garage holds one, and a second restore before the first was
 * seen replaces it rather than queuing a backlog nobody asked for.
 */
internal class PrefsRestoredHistoryStore(context: Context) : RestoredHistoryStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getString(KEY_CAR_ID, null)?.let(::CarId))

    override fun pending(): Flow<CarId?> = state.asStateFlow()

    override suspend fun record(carId: CarId) {
        // Already pending for this car: recording it again from the other restore path must
        // not look like a second restore.
        if (state.value == carId) return
        prefs.edit { putString(KEY_CAR_ID, carId.value) }
        state.value = carId
    }

    override suspend fun clear() {
        prefs.edit { remove(KEY_CAR_ID) }
        state.value = null
    }

    private companion object {
        const val PREFS_NAME = "odo_restored_history"
        const val KEY_CAR_ID = "pending_car_id"
    }
}
