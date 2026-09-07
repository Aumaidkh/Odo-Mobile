package com.hopcape.odo

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.history.RestoredHistoryStore
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

private typealias RestoreTestRule =
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/**
 * Driving the "your history came back" sheet (issue #425).
 *
 * The marker is pushed straight into [RestoredHistoryStore] rather than through prefs: the
 * store is a Koin singleton holding the flow the app shell collects, so a prefs write behind
 * its back would land in the file and never reach the screen.
 */
internal fun announceRestore(carId: String = LogFixtures.CAR) {
    val store: RestoredHistoryStore = GlobalContext.get().get()
    runBlocking { store.record(CarId(carId)) }
}

/** Put the owner's own reading where a test needs it, without going through the sheet. */
internal fun setCarOdometer(km: Int) = with(GlobalContext.get().get<SqlDriver>()) {
    execute(null, "UPDATE cars SET current_odometer_km = $km WHERE id = '${LogFixtures.CAR}'", 0)
    notifyListeners("cars")
}

/**
 * Close the sheet the way an owner does, so the next capture starts from the dashboard.
 *
 * The low-emphasis button is worded differently in the two shapes — "Theek hai" when there
 * is nothing to correct, "Nahi, … hi rakho" when there is — so this takes whichever is on
 * screen rather than assuming one.
 */
internal fun RestoreTestRule.dismissRestoredHistory() {
    val keep = onAllNodesWithText(RestoredHistoryCopy.KEEP_PREFIX, substring = true)
    if (keep.fetchSemanticsNodes().isNotEmpty()) {
        keep.onFirst().performClick()
    } else {
        onNodeWithText(RestoredHistoryCopy.DONE).performClick()
    }
    awaitGone(RestoredHistoryCopy.TITLE)
}
