package com.hopcape.odo

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.feature.garage.presentation.GarageTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * One-off, throwaway verification for the "still shows 500" bug report: the owner updates
 * the odometer to 500 km via the garage sheet (the real write path — straight to
 * `cars.current_odometer_km`/`odometer_updated_at`, no `service_logs` row), then a 5 km
 * auto-trip lands on the SAME day. Confirms the garage now reads 505 km in a real running
 * app, not just in a fake-backed unit test.
 */
@RunWith(AndroidJUnit4::class)
class AggregateOdometerVerificationTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sameDayTripAfterAManualOdometerUpdate_addsOnTop() {
        // Half of this is the trip-logged redirect, which 1.0 does not have.
        resetAutoOdometer()
        seedOnboardedOwner() // baseline: 40,000 km
        rule.activityRule.scenario.recreate()

        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)
        rule.openOdometerSheet()
        // The update sheet refuses a reading below what's on record — must be ascending.
        rule.saveOdometer("41000")
        rule.awaitGone(GarageCopy.ODOMETER_SAVE)
        rule.onNodeWithTag(GarageTestTags.ODOMETER).assertTextEquals("41,000 km")

        val driver: SqlDriver = GlobalContext.get().get()
        val today = driver.executeQuery(
            identifier = null,
            sql = "SELECT odometer_updated_at FROM cars WHERE id = '${LogFixtures.CAR}'",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getString(0) else null,
                )
            },
            parameters = 0,
        ).value ?: error("odometer_updated_at was not stamped by saveOdometer")

        seedCountedTrip(
            id = "verify-same-day-trip",
            startedAt = today,
            endedAt = today,
            distanceM = 5_000,
        )

        // The app-shell redirect (D4) fires live, no recreate needed — same as
        // AutoOdometerEndToEndTest's own seeded-trip tests.
        rule.awaitTripLoggedRedirect()
        rule.onNodeWithText(AutoOdometerCopy.TRIP_LOGGED_DONE).performClick()
        rule.awaitGone(AutoOdometerCopy.TRIP_LOGGED_TITLE)
        rule.awaitText(GarageCopy.TITLE)

        rule.onNodeWithTag(GarageTestTags.ODOMETER).assertTextEquals("41,005 km")
    }
}
