package com.hopcape.odo

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.core.triptracker.BluetoothRadio
import com.hopcape.odo.core.triptracker.BondedDevice
import com.hopcape.odo.core.triptracker.BondedDeviceCatalog
import com.hopcape.odo.core.triptracker.DeviceCategory
import com.hopcape.odo.core.triptracker.TripTracker
import com.hopcape.odo.core.triptracker.VehicleBondStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/**
 * The words the auto-odometer screens and the garage's card put on screen, mirrored from
 * `ga_`/`ao_`-prefixed `strings.xml` entries (docs/AUTO_ODOMETER_PLAN.md §8). Copied rather
 * than read, for the same reason as [VaultCopy]: Compose Resources keeps a feature's
 * generated `Res` internal to its own module. Typographic apostrophes (’) come straight
 * from the resource files.
 */
internal object AutoOdometerCopy {
    /* Garage card / status tile — `ga_` keys, live in :feature:garage. */
    const val CARD_TITLE = "Auto odometer"
    const val CARD_CTA = "See how it works"

    /** `"Auto odometer on · %1$d km this month"` — the post-setup status tile. */
    fun statusTile(km: Long) = "Auto odometer on · $km km this month"

    /* Education (M2). */
    const val EDUCATION_TITLE = "Your reading stays current on its own"
    const val EDUCATION_CTA_STEREO = "Pair my car"

    /* Device picker (M3). */
    const val PICKER_TITLE = "Which one is your car?"
    const val PICKER_SECTION_OTHER = "OTHER PAIRED DEVICES"

    /** `"Use %1$s"` — the picker's primary CTA, once a device is selected. */
    fun pickerCta(deviceName: String) = "Use $deviceName"

    /* Settings (M7). Permission setup (M4) has no assertions of its own in this suite: with
     * every step's permission already granted via GrantPermissionRule, the checklist
     * advances itself without ever showing a priming card. */
    const val SETTINGS_TITLE = "Auto odometer"
    const val SETTINGS_TRACKING_ON = "Tracking is on"
    const val SETTINGS_SECTION_TRIGGER_DEVICE = "TRIGGER DEVICE"
    const val SETTINGS_LOGS_TO_STEREO = "Your car’s stereo connecting"

    /* Trip logged (M6). */
    const val TRIP_LOGGED_TITLE = "Trip logged"
    const val TRIP_LOGGED_DONE = "Done"
    const val TRIP_LOGGED_REJECT = "That wasn’t me driving"
    const val TRIP_LOGGED_REJECT_CONFIRM_CTA = "Remove trip"
}

/** The synthetic device the fake [BondedDeviceCatalog] hands the picker, and the trip this suite seeds. */
internal object AutoOdometerFixtures {
    const val DEVICE_ID = "ao-e2e-bt-1"
    const val DEVICE_NAME = "Test Car Stereo"

    const val TRIP_ID = "ao-e2e-trip-1"
    const val TRIP_DISTANCE_M = 15_000
    const val TRIP_STARTED_AT = "2026-08-06T09:00:00Z"
    const val TRIP_ENDED_AT = "2026-08-06T09:24:00Z"
}

private typealias AutoOdometerTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Database + Koin state ------------------------------ */

private fun autoOdometerDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empties everything the auto-odometer flows read or write, on top of [resetGarage] (the
 * car, its logs and its documents — the auto-odometer card is a slot on the garage screen).
 *
 * Two pieces of state live outside the SQLite DB and outlive `MainActivity.recreate()`
 * because they are process-lifetime Koin singletons, so a test that does not reset them
 * would be driving a device a previous test already half set up:
 * - [VehicleBondStore] is real `SharedPreferences` (F1's choice, `PrefsVehicleBondStore`) —
 *   cleared directly rather than through a SQL table.
 * - [TripTracker.isEnabled] is an in-memory flag on the `DefaultTripTracker` singleton, not
 *   yet persisted anywhere — set back to `false` the same way a settings-screen toggle would.
 */
internal fun resetAutoOdometer() {
    resetGarage()
    with(autoOdometerDriver()) {
        execute(null, "DELETE FROM trips", 0)
        execute(null, "DELETE FROM app_settings", 0)
        notifyListeners("trips")
        notifyListeners("app_settings")
    }
    runBlocking {
        GlobalContext.get().get<VehicleBondStore>().clearBond()
        GlobalContext.get().get<TripTracker>().setEnabled(false)
    }
}

/**
 * Puts a fake [BondedDeviceCatalog] in front of the device picker (docs/AUTO_ODOMETER_PLAN.md
 * §7 F10's explicit "picker (fake catalog)" instruction) — there is no real Bluetooth adapter
 * on a CI emulator to pair a device against. `allowOverride` mirrors [installBillExtractor]'s
 * pattern for the same reason: the shipped binding (`AndroidBondedDeviceCatalog`) would
 * otherwise throw or answer empty.
 *
 * The synthetic device is handed back with `isConnectedNow = false` on purpose: a
 * `CAR_AUDIO` device the engine's own presence trigger believes is live would let
 * `TripTracker.startIfConnected()` (run from `CompleteSetup`) start a real foreground
 * tracking session against fabricated data, which this suite has no interest in exercising.
 * The owner picks it manually from "OTHER PAIRED DEVICES" instead, which still exercises
 * every step the plan asks for (grouping, selection, enroll).
 *
 * [BluetoothRadio] is faked to read on alongside it, for the same reason and in the same
 * breath: the picker gates its list on the radio as well as on the catalog, so an emulator
 * with Bluetooth switched off would draw the radio-off card and this suite would never reach
 * the device list at all. Faking one without the other would make these tests pass or fail on
 * a setting of the host machine.
 */
internal fun installFakeBondedDeviceCatalog(devices: List<BondedDevice>) {
    GlobalContext.get().loadModules(
        listOf(
            module {
                single<BondedDeviceCatalog> {
                    object : BondedDeviceCatalog {
                        override suspend fun devices(): List<BondedDevice> = devices
                    }
                }
                single<BluetoothRadio> {
                    object : BluetoothRadio {
                        override val enabled: Flow<Boolean> = flowOf(true)
                    }
                }
            },
        ),
        allowOverride = true,
    )
}

/** The one synthetic device this suite drives the picker with. */
internal fun defaultFakeDevice() = BondedDevice(
    id = AutoOdometerFixtures.DEVICE_ID,
    name = AutoOdometerFixtures.DEVICE_NAME,
    category = DeviceCategory.CAR_AUDIO,
    isConnectedNow = false,
)

/**
 * A [com.hopcape.odo.core.domain.trip.model.TripStatus.RECORDED] trip for the seeded car,
 * written straight to the database — the engine that produces one has its own coverage in
 * `:core:triptracker`, and this suite only needs the state it leaves behind (the same
 * reasoning [seedOverdueService] uses). Dated after every reading [seedServiceHistory]
 * writes, so [ObserveDerivedOdometer][com.hopcape.odo.feature.autoodometer.domain.usecase.ObserveDerivedOdometer]
 * has a manual-reading anchor to sum it onto.
 */
internal fun seedCountedTrip(
    id: String = AutoOdometerFixtures.TRIP_ID,
    startedAt: String = AutoOdometerFixtures.TRIP_STARTED_AT,
    endedAt: String = AutoOdometerFixtures.TRIP_ENDED_AT,
    distanceM: Int = AutoOdometerFixtures.TRIP_DISTANCE_M,
) = with(autoOdometerDriver()) {
    execute(
        null,
        "INSERT INTO trips (id, car_id, owner_id, started_at, ended_at, distance_m, estimated_m, mode, " +
            "status, created_at, updated_at, sync_status) VALUES ('$id', '${LogFixtures.CAR}', " +
            "'${LogFixtures.OWNER}', '$startedAt', '$endedAt', $distanceM, 0, 'BT_VERIFIED', 'RECORDED', " +
            "'$startedAt', '$startedAt', 'PENDING')",
        0,
    )
    notifyListeners("trips")
}

/** Read a trip's status straight back — what the reject path is asserted against. */
internal fun storedTripStatus(id: String): String? = autoOdometerDriver().executeQuery(
    identifier = null,
    sql = "SELECT status FROM trips WHERE id = '$id'",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
    parameters = 0,
).value

/* ------------------------------ Navigation ------------------------------ */

/** Tap the garage card's CTA and land on the education screen (M1 → M2). */
internal fun AutoOdometerTestRule.tapAutoOdometerCard() {
    onNodeWithText(AutoOdometerCopy.CARD_TITLE).performScrollTo()
    onNodeWithText(AutoOdometerCopy.CARD_CTA).performScrollTo().performClick()
    awaitText(AutoOdometerCopy.EDUCATION_TITLE)
}

/** Tap the education screen's "Pair my car" and land on the device picker (M2 → M3). */
internal fun AutoOdometerTestRule.tapEducationCta() {
    onNodeWithText(AutoOdometerCopy.EDUCATION_CTA_STEREO).performClick()
    awaitText(AutoOdometerCopy.PICKER_TITLE)
}

/**
 * Pick [deviceName] on the device picker and enroll it. With `BLUETOOTH_CONNECT`,
 * `POST_NOTIFICATIONS` and `ACCESS_FINE_LOCATION` already granted via `GrantPermissionRule`,
 * the permission checklist (M4) that follows advances itself — every step reads Granted on
 * its first composition, which is the same "already granted, no tap needed" path
 * `PermissionSetupViewModel` exercises on its own — so this returns once the flow has
 * completed all the way back to the garage tab (docs/AUTO_ODOMETER_PLAN.md's locked
 * navigation-flow decision), never showing a single system dialog.
 */
internal fun AutoOdometerTestRule.pickDeviceAndCompleteSetup(deviceName: String = AutoOdometerFixtures.DEVICE_NAME) {
    onNodeWithText(deviceName).performClick()
    onNodeWithText(AutoOdometerCopy.pickerCta(deviceName)).performClick()
    awaitText(GarageCopy.TITLE, timeoutMillis = 15_000L)
}

/** Open settings from the garage's post-setup status tile. */
internal fun AutoOdometerTestRule.openAutoOdometerSettingsFromStatusTile(monthlyKm: Long = 0) {
    onNodeWithText(AutoOdometerCopy.statusTile(monthlyKm)).performScrollTo().performClick()
    awaitText(AutoOdometerCopy.SETTINGS_TITLE)
}

/** Wait for the app-shell redirect (D4) to surface the trip-logged screen for a pending trip. */
internal fun AutoOdometerTestRule.awaitTripLoggedRedirect() {
    awaitText(AutoOdometerCopy.TRIP_LOGGED_TITLE, timeoutMillis = 15_000L)
}
