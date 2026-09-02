package com.hopcape.odo

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import org.koin.core.context.GlobalContext
import java.time.LocalDate

/**
 * The words smart refuel puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason as [CostCopy]: Compose Resources keeps a
 * feature's generated `Res` internal to its own module, so `:androidApp` cannot reach it.
 * Asserting on the copy an owner actually reads is the point.
 */
internal object RefuelCopy {
    /* The way in. */
    const val LOG_FILL_ACTION = "Log a fill"

    /* The prefilled form. */
    const val LOG_TITLE = "Log a fill"
    const val PREFILL_NOTE = "Filled in from your last visit. Change what’s different."
    const val STATION_LABEL = "Station"
    const val STATION_NOTE = "last used"
    const val RATE_CARRIED = "carried forward"
    const val ODOMETER_PREDICTED = "predicted"
    const val AMOUNT_LABEL = "AMOUNT — THE ONLY THING TO TYPE"
    const val SCAN_PUMP_CHIP = "Scan pump"
    const val LOG_DONE = "Done"

    /* The confirm surface every channel lands on. */
    const val CONFIRM_TITLE = "Confirm this fill"
    const val CONFIRM_CTA = "Confirm & log"
    const val CONFIRM_REJECT = "This wasn’t fuel"
    const val ODOMETER_WARNING = "PREDICTED — CHECK IT"
    const val ORIGIN_HISTORY = "FROM HISTORY"
    const val ORIGIN_DERIVED = "CALCULATED"

    /* The success screen. */
    const val LOGGED_TITLE = "Logged"
    const val LOGGED_FUEL_ADDED = "Fuel added"
    const val LOGGED_ODOMETER = "Odometer"
    const val LOGGED_SOURCE = "Source"
    const val SOURCE_PREFILLED = "PREFILLED"
    const val LOGGED_DONE = "Done"
    const val LOGGED_VIEW_TIMELINE = "View in Timeline"

    /* The scanner's new mode. */
    const val PUMP_MODE_CHIP = "Pump"
    const val PUMP_SCAN_TITLE = "Scan the pump display"
}

/**
 * The car, its last fill and the numbers the prefill is supposed to produce from them.
 *
 * Dates are relative to the day the test runs: the odometer prediction is a rate over
 * elapsed days, so a fixed date would change what it predicts every time the suite runs.
 */
internal object RefuelFixtures {
    const val CAR = "refuel-car"
    const val OWNER = "refuel-owner"

    /** The car's baseline reading, sixty days back — enough history to imply a rate. */
    const val BASELINE_KM = 30_000
    const val BASELINE_DAYS_AGO = 60L

    /** A service twenty days back at 33,000 km: 3,000 km over forty days is 75 a day. */
    const val SERVICE_ID = "refuel-log"
    const val SERVICE_DAYS_AGO = 20L
    const val SERVICE_KM = 33_000

    /** The previous fill, which is where the station and the rate are carried from. */
    const val LAST_FILL_ID = "refuel-fill-1"
    const val LAST_FILL_DAYS_AGO = 20L
    const val LAST_FILL_KM = 33_000
    const val LAST_FILL_STATION = "Bharat Petroleum, Karol Bagh"

    /** Rs. 2,000 for 20 litres — a round Rs. 100 a litre, so the derived figures are exact. */
    const val LAST_FILL_PAISE = 200_000L
    const val LAST_FILL_MILLI = 20_000L

    /** What the owner types, and the litres it must work out to at the carried-forward rate. */
    const val AMOUNT_TYPED = "1500"
    const val EXPECTED_LITRES = "15"
}

private typealias RefuelTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

private fun refuelDriver(): SqlDriver = GlobalContext.get().get()

/** A finished setup: an onboarded owner and one car with a reading two months old. */
internal fun seedRefuelOwner() = with(refuelDriver()) {
    execute(
        null,
        "INSERT INTO profiles (id, full_name, onboarding_completed_at, city, " +
            "created_at, updated_at, sync_status) VALUES ('${RefuelFixtures.OWNER}', 'Rohit', " +
            "'$REFUEL_SEEDED_AT', 'Pune', '$REFUEL_SEEDED_AT', '$REFUEL_SEEDED_AT', 'PENDING')",
        0,
    )
    execute(
        null,
        "INSERT INTO cars (id, owner_id, make, model, variant, year, fuel_type, registration_number, " +
            "current_odometer_km, is_primary, created_at, updated_at, sync_status) VALUES " +
            "('${RefuelFixtures.CAR}', '${RefuelFixtures.OWNER}', 'Maruti Suzuki', 'Swift', 'VXI', 2020, " +
            "'PETROL', 'MH12AB1234', ${RefuelFixtures.BASELINE_KM}, 1, " +
            "'${refuelDaysAgo(RefuelFixtures.BASELINE_DAYS_AGO)}T00:00:00Z', '$REFUEL_SEEDED_AT', 'PENDING')",
        0,
    )
    announceRefuelWrites()
}

/**
 * One service and one earlier fill.
 *
 * Both are needed and for different reasons: the service is the odometer reading the
 * prediction projects forward from, and the fill is where the station and the rate are
 * carried from. A prefill that only had one of them would be half-filled, which is a
 * different screen from the one under test.
 */
internal fun seedRefuelHistory() = with(refuelDriver()) {
    execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, " +
            "workshop_name, notes, source, bill_id, bill_photo_path, fairness_snapshot, created_at, " +
            "updated_at, sync_status) VALUES ('${RefuelFixtures.SERVICE_ID}', '${RefuelFixtures.CAR}', " +
            "'${RefuelFixtures.OWNER}', '${refuelDaysAgo(RefuelFixtures.SERVICE_DAYS_AGO)}', " +
            "${RefuelFixtures.SERVICE_KM}, 500000, 'Sharma Motors', NULL, 'MANUAL', NULL, NULL, NULL, " +
            "'$REFUEL_SEEDED_AT', '$REFUEL_SEEDED_AT', 'PENDING')",
        0,
    )
    execute(
        null,
        "INSERT INTO fuel_fills (id, car_id, owner_id, filled_on, odometer_km, quantity_milli, fuel_unit, " +
            "amount_paise, station_name, transaction_ref, entry_source, created_at, updated_at, " +
            "sync_status) VALUES ('${RefuelFixtures.LAST_FILL_ID}', '${RefuelFixtures.CAR}', " +
            "'${RefuelFixtures.OWNER}', '${refuelDaysAgo(RefuelFixtures.LAST_FILL_DAYS_AGO)}', " +
            "${RefuelFixtures.LAST_FILL_KM}, ${RefuelFixtures.LAST_FILL_MILLI}, 'LITRE', " +
            "${RefuelFixtures.LAST_FILL_PAISE}, '${RefuelFixtures.LAST_FILL_STATION}', NULL, " +
            "'MANUAL', '$REFUEL_SEEDED_AT', '$REFUEL_SEEDED_AT', 'PENDING')",
        0,
    )
    announceRefuelWrites()
}

/**
 * The owner's own fuel rate, so the litres the screen computes do not depend on Odo's
 * seeded prices.
 *
 * Those are reference data any release may correct, and a suite that pinned them would fail
 * on a correction rather than on a bug. Rs. 100 a litre makes every derived figure round.
 */
internal fun seedRefuelOwnerRate(paisePerLitre: Long = 10_000) = with(refuelDriver()) {
    execute(
        null,
        "INSERT INTO fuel_price (id, city, fuel_type, paise_per_unit, effective_date, source) VALUES " +
            "('refuel-rate', '', 'PETROL', $paisePerLitre, '${LocalDate.now()}', 'OWNER')",
        0,
    )
    notifyListeners("fuel_price")
}

/** Every fill the car has, newest first, as the columns a test asserts on. */
internal fun refuelFills(): List<StoredFill> = refuelDriver().executeQuery(
    identifier = null,
    sql = "SELECT id, amount_paise, quantity_milli, odometer_km, entry_source, station_name " +
        "FROM fuel_fills WHERE deleted_at IS NULL ORDER BY created_at DESC",
    mapper = { cursor ->
        val rows = mutableListOf<StoredFill>()
        while (cursor.next().value) {
            rows += StoredFill(
                id = cursor.getString(0).orEmpty(),
                amountPaise = cursor.getLong(1) ?: 0L,
                quantityMilli = cursor.getLong(2) ?: 0L,
                // Genuinely nullable now — the odometer is optional on a fill, and
                // reading a missing one back as 0 would hide exactly that.
                odometerKm = cursor.getLong(3)?.toInt(),
                entrySource = cursor.getString(4).orEmpty(),
                stationName = cursor.getString(5),
            )
        }
        QueryResult.Value(rows.toList())
    },
    parameters = 0,
).value

/** One row of `fuel_fills`, as the suite reads it back. */
internal data class StoredFill(
    val id: String,
    val amountPaise: Long,
    val quantityMilli: Long,
    val odometerKm: Int?,
    val entrySource: String,
    val stationName: String?,
)

/** Clear everything refuel reads, including any rate a previous test set. */
internal fun resetRefuel() {
    resetOwnerData()
    with(refuelDriver()) {
        execute(null, "DELETE FROM fuel_fills", 0)
        execute(null, "DELETE FROM fuel_price WHERE source = 'OWNER'", 0)
        execute(null, "DELETE FROM ignored_merchants", 0)
        execute(null, "DELETE FROM refuel_detection_settings", 0)
        notifyListeners("fuel_fills", "fuel_price", "ignored_merchants", "refuel_detection_settings")
    }
}

/**
 * Tell SQLDelight that these tables changed — seeds are hand-written SQL, which goes in
 * behind its back. See the service-log robot for why this matters between test classes.
 */
private fun announceRefuelWrites() = refuelDriver().notifyListeners(
    "cars",
    "profiles",
    "service_logs",
    "fuel_fills",
    "fuel_price",
)

private fun refuelDaysAgo(days: Long): String = LocalDate.now().minusDays(days).toString()

private const val REFUEL_SEEDED_AT = "2026-01-01T00:00:00Z"
