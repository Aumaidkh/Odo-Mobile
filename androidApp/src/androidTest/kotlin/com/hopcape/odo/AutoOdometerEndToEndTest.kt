package com.hopcape.odo

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hopcape.odo.core.common.FeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Every auto-odometer flow docs/AUTO_ODOMETER_PLAN.md §7 F10 names, driven against the real
 * app: the real Koin graph, the real SQLite database, the real navigation graph and the real
 * ViewModels.
 *
 * **What is faked, and only this.** [BondedDeviceCatalog][com.hopcape.odo.core.triptracker.BondedDeviceCatalog]
 * — there is no real Bluetooth adapter to pair a device against on a CI emulator, and the
 * plan's own F10 scope names this fake explicitly. Everything above the port is the shipped
 * code: the picker's grouping, the enroll use case, the real `VehicleBondStore`
 * (`SharedPreferences`), the real permission checklist state machine and the real garage
 * card/status-tile aggregation.
 *
 * **Why no system permission dialog is ever tapped.** `BLUETOOTH_CONNECT`,
 * `POST_NOTIFICATIONS`, `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION` are
 * granted for the whole class, the same
 * choice [BillScannerEndToEndTest] makes for `CAMERA` — an instrumented test cannot drive an
 * OS dialog reliably across versions, and what Odo owns is the screen in front of it. With
 * every step already granted, the permission checklist (M4) advances itself the moment each
 * screen mounts (`PermissionSetupViewModel.statusObserved`'s "already granted, no tap needed"
 * path — see its own `alreadyGrantedStep_onFirstRead_advancesWithoutATap` unit test), so this
 * suite never even renders a priming card.
 *
 * **NO_STEREO, `ACTIVITY_RECOGNITION`, the live tracking notification (M5) and a real
 * foreground trip session are out of scope here** — scoped down per the F10 brief's own
 * allowance ("if the existing E2E infrastructure makes some of this impractical... it's fine
 * to scope down"). Reasons: `ACTIVITY_RECOGNITION` only matters on the NO_STEREO escape
 * hatch, which this suite never takes (the STEREO happy path is what the plan's flow list
 * names); and a live FGS session needs a real location fix stream an emulator cannot
 * reliably produce without flaking the suite, exactly the concern the plan flags for "real
 * Bluetooth/location permission dialogs". The fake catalog's device is deliberately reported
 * as *not* currently connected (see [defaultFakeDevice]) so `CompleteSetup`'s
 * `startIfConnected()` never tries to start one. Trip-logged is instead reached the way an
 * owner not mid-drive reaches it — a trip the engine already finished, seeded straight into
 * the database (the same "seed what a flow leaves behind" convention every other suite here
 * uses for a source it does not own).
 *
 * **Before running:** clear the app's data or uninstall. F1 added two new nullable columns
 * to `app_settings`, and this repo has no real migrations yet — an already-installed debug
 * build keeps its old schema and every write to `app_settings` fails against it.
 *
 * **1.0 skips all of it.** Every flow below starts at the garage card, which
 * [FeatureFlags.AUTO_ODOMETER_ENABLED] hides, so each test is guarded by `assumeTrue` and
 * [theGarageShowsNoAutoOdometerCardWhileTheFeatureIsOff] is the one that runs instead.
 */
@RunWith(AndroidJUnit4::class)
class AutoOdometerEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Conditional on the flag, because 1.0's manifest declares none of these — and
     * `GrantPermissionRule` fails outright on a permission the app under test never asked
     * for, before any `assumeTrue` in a test body gets to skip it.
     */
    @get:Rule
    val trackingPermissions: TestRule =
        if (FeatureFlags.AUTO_ODOMETER_ENABLED) {
            GrantPermissionRule.grant(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        } else {
            TestRule { base, _ -> base }
        }

    /**
     * Start every test from a set-up device with a car, >= 2 manual readings (the garage
     * card's own visibility rule, plan §1's convention) and nothing auto-odometer has
     * touched yet.
     */
    @Before
    fun startFromASetUpDevice() {
        resetAutoOdometer()
        seedOnboardedOwner()
        seedServiceHistory()
        installFakeBondedDeviceCatalog(listOf(defaultFakeDevice()))
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------------ Flow 1: garage card -> education ------------------------------ */

    @Test
    fun theGarageCardOpensTheEducationScreen() {
        assumeTrue(FeatureFlags.AUTO_ODOMETER_ENABLED)
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)

        rule.tapAutoOdometerCard()

        rule.onNodeWithText(AutoOdometerCopy.EDUCATION_TITLE).assertIsDisplayed()
        rule.onNodeWithText(AutoOdometerCopy.EDUCATION_CTA_STEREO).assertIsDisplayed()
    }

    /* ------------------------------ Flow 2: education -> device picker ------------------------------ */

    @Test
    fun educationsCtaOpensTheDevicePickerWithTheFakeCatalogsDevice() {
        assumeTrue(FeatureFlags.AUTO_ODOMETER_ENABLED)
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)
        rule.tapAutoOdometerCard()

        rule.tapEducationCta()

        // Not reported connected, so it lands in "other paired devices" rather than
        // pre-selected under "connected now" — see installFakeBondedDeviceCatalog's KDoc.
        rule.onNodeWithText(AutoOdometerCopy.PICKER_SECTION_OTHER).assertIsDisplayed()
        rule.onNodeWithText(AutoOdometerFixtures.DEVICE_NAME).assertIsDisplayed()
    }

    /* ------------------------------ Flows 3 + 4: pick -> permissions -> garage on ------------------------------ */

    @Test
    fun pickingTheDeviceCompletesSetupAndTurnsTrackingOnFromTheGarage() {
        assumeTrue(FeatureFlags.AUTO_ODOMETER_ENABLED)
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)
        rule.tapAutoOdometerCard()
        rule.tapEducationCta()

        rule.pickDeviceAndCompleteSetup()

        // The card slot now renders the compact status tile instead of the pitch card —
        // nothing counted yet, so 0 km this month.
        rule.onNodeWithText(AutoOdometerCopy.statusTile(0)).assertIsDisplayed()
        rule.onNodeWithText(AutoOdometerCopy.CARD_CTA).assertDoesNotExist()
    }

    /* ------------------------------ Flow 5: settings ------------------------------ */

    @Test
    fun settingsShowsTrackingOnAndTheEnrolledTriggerDevice() {
        assumeTrue(FeatureFlags.AUTO_ODOMETER_ENABLED)
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)
        rule.tapAutoOdometerCard()
        rule.tapEducationCta()
        rule.pickDeviceAndCompleteSetup()

        rule.openAutoOdometerSettingsFromStatusTile(monthlyKm = 0)

        rule.onNodeWithText(AutoOdometerCopy.SETTINGS_TRACKING_ON).assertIsDisplayed()
        rule.onNodeWithText(AutoOdometerCopy.SETTINGS_SECTION_TRIGGER_DEVICE).assertIsDisplayed()
        // VehicleBond only stores the bonded Bluetooth id, not a name (SettingsScreen's own
        // KDoc on ao_settings_waiting_for) — the row says how a trip starts, not which
        // device, so this is the STEREO mode's fixed line rather than the fixture's name.
        rule.onNodeWithText(AutoOdometerCopy.SETTINGS_LOGS_TO_STEREO).assertIsDisplayed()
    }

    /* ------------------------------ Trip logged: acknowledge ------------------------------ */

    @Test
    fun aSeededTripRedirectsToTripLogged_andDoneAdvancesPastTheScreen() {
        assumeTrue(FeatureFlags.AUTO_ODOMETER_ENABLED)
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)

        seedCountedTrip()

        rule.awaitTripLoggedRedirect()
        rule.onNodeWithText(AutoOdometerCopy.TRIP_LOGGED_DONE).assertIsDisplayed()

        rule.onNodeWithText(AutoOdometerCopy.TRIP_LOGGED_DONE).performClick()

        // Acknowledging pops back to wherever the redirect fired from — the garage tab.
        rule.awaitGone(AutoOdometerCopy.TRIP_LOGGED_TITLE)
        rule.awaitText(GarageCopy.TITLE)
    }

    /* ------------------------------ Trip logged: reject ------------------------------ */

    @Test
    fun aSeededTripsRejectPathAsksToConfirm_andMarksItRejected() {
        assumeTrue(FeatureFlags.AUTO_ODOMETER_ENABLED)
        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)

        seedCountedTrip()
        rule.awaitTripLoggedRedirect()

        rule.onNodeWithText(AutoOdometerCopy.TRIP_LOGGED_REJECT).performClick()
        rule.awaitText(AutoOdometerCopy.TRIP_LOGGED_REJECT_CONFIRM_CTA)
        rule.onNodeWithText(AutoOdometerCopy.TRIP_LOGGED_REJECT_CONFIRM_CTA).performClick()

        rule.waitUntil(5_000) { storedTripStatus(AutoOdometerFixtures.TRIP_ID) == "REJECTED" }
        assertEquals("REJECTED", storedTripStatus(AutoOdometerFixtures.TRIP_ID))
    }

    /* ------------------------------ What 1.0 ships instead ------------------------------ */

    /**
     * The twin of every test above. Same seeding — a car with enough manual readings to
     * earn the pitch card — and the slot stays empty, so there is no first step to take.
     */
    @Test
    fun theGarageShowsNoAutoOdometerCardWhileTheFeatureIsOff() {
        assumeFalse(FeatureFlags.AUTO_ODOMETER_ENABLED)

        rule.openGarage()
        rule.awaitText(GarageCopy.HISTORY)

        rule.onNodeWithText(AutoOdometerCopy.CARD_TITLE).assertDoesNotExist()
        rule.onNodeWithText(AutoOdometerCopy.CARD_CTA).assertDoesNotExist()
    }
}
