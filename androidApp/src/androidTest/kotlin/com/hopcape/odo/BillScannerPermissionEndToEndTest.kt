package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What an owner sees before the camera has been allowed.
 *
 * Its own class because the permission decides the whole screen, and a `GrantPermissionRule`
 * applies to every test in the class that declares it — so the granted flows live in
 * [BillScannerEndToEndTest] and the ungranted ones live here.
 *
 * **These skip themselves if the camera is already allowed**, and that is deliberate. A grant
 * survives for the life of the install, including one made by another class's
 * `GrantPermissionRule`, and a permission cannot be taken back from inside the process it
 * belongs to: `pm revoke` kills the app, and in an instrumented run the app *is* the test.
 * Skipping says so; failing would only look like a bug in the screen.
 *
 * **So run these on a fresh install** — `adb uninstall com.hopcape.odo` first, or run this
 * class before [BillScannerEndToEndTest].
 *
 * The system permission dialog itself is never tapped. It belongs to the OS, an instrumented
 * test cannot drive it reliably across versions, and what Odo owns is the screen in front of
 * it — which is the part that decides whether the prompt is ever seen with any context.
 */
@RunWith(AndroidJUnit4::class)
class BillScannerPermissionEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun startOnASetUpDeviceWithNoCameraPermission() {
        assumeFalse(
            "CAMERA is already granted for this install — uninstall the app and run this class first.",
            cameraPermissionGranted(),
        )
        resetScanner()
        seedOnboardedOwner()
        installDefaultScanning()
        rule.activityRule.scenario.recreate()
    }

    @Test
    fun openingTheScannerExplainsWhyTheCameraIsNeededBeforeTheSystemAsks() {
        rule.openScanner()

        // Android gives one second chance and iOS gives none, so the single prompt an owner
        // sees should follow an explanation rather than arrive cold.
        rule.awaitText(ScanCopy.CAMERA_TITLE)
        rule.onNodeWithText(ScanCopy.CAMERA_SUBTITLE).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_ALLOW).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).assertIsDisplayed()
    }

    @Test
    fun theRationaleNamesAllThreeThingsTheOneGrantUnlocks() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)

        // Asking three separate times for one permission would be asking for the same thing
        // three times.
        rule.onNodeWithText(ScanCopy.CAMERA_BILLS).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_PAPERS).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_QR).assertIsDisplayed()
    }

    @Test
    fun theRationaleStatesWhatOdoWillNotDoWithTheCamera() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)

        rule.onNodeWithText(ScanCopy.CAMERA_NO_BACKGROUND).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_ONLY_ON_SCAN).assertIsDisplayed()
    }

    @Test
    fun decliningLeavesAWayBackInsteadOfADeadScreen() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)

        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).performClick()

        // The scanner still opens — with the frame, the mode chips and a nudge that offers
        // the permission again. A blank screen with no explanation would be the alternative.
        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)
        rule.awaitText(ScanCopy.NUDGE)
        rule.onNodeWithText(ScanCopy.NUDGE_ALLOW).assertIsDisplayed()
        // Typing the entry in by hand still works without a camera, so it stays offered.
        rule.onNodeWithText(ScanCopy.MANUAL).assertIsDisplayed()
    }

    @Test
    fun theRationaleDoesNotComeBackOnceItHasBeenWavedAway() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)
        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).performClick()
        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)

        // Switching what is being scanned must not re-open the screen the owner just closed.
        rule.selectScanMode(ScanCopy.MODE_DOCUMENT)

        rule.awaitText(ScanCopy.SCAN_TITLE_DOCUMENT)
        rule.awaitGone(ScanCopy.CAMERA_TITLE)
    }

    @Test
    fun theTorchIsNotOfferedWhileTheCameraIsNotAllowed() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)
        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).performClick()
        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)

        // A light that cannot be turned on is a control that does nothing.
        rule.onNodeWithLabel(ScanCopy.TORCH_ON).assertDoesNotExist()
    }
}
