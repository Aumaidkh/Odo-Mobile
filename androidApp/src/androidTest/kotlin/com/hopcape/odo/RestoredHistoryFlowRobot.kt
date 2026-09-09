package com.hopcape.odo

import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.history.RestoredHistoryStore
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * Driving the "your history is back" sheet (issue #425).
 *
 * The marker is pushed straight into [RestoredHistoryStore] rather than through prefs: the
 * store is a Koin singleton holding the flow the app shell collects, so a prefs write behind
 * its back would land in the file and never reach the screen.
 */
internal fun announceRestore(carId: String = LogFixtures.CAR) {
    val store: RestoredHistoryStore = GlobalContext.get().get()
    runBlocking { store.record(CarId(carId)) }
}

/** Forget any restore left pending, so a test starts with nothing waiting to be announced. */
internal fun clearPendingRestore() {
    val store: RestoredHistoryStore = GlobalContext.get().get()
    runBlocking { store.clear() }
}

/**
 * Clear the two tables the sheet counts that [resetOwnerData] leaves behind.
 *
 * Without this a second run in the same process fails on a duplicate document id, and any
 * fuel fill another test left would be counted as part of this restore.
 */
internal fun resetRestorableRecords() = with(GlobalContext.get().get<SqlDriver>()) {
    execute(null, "DELETE FROM documents", 0)
    execute(null, "DELETE FROM fuel_fills", 0)
    notifyListeners("documents", "fuel_fills")
}

/** Put the owner's own reading where a test needs it, without going through the sheet. */
internal fun setCarOdometer(km: Int) = with(GlobalContext.get().get<SqlDriver>()) {
    execute(null, "UPDATE cars SET current_odometer_km = $km WHERE id = '${LogFixtures.CAR}'", 0)
    notifyListeners("cars")
}
