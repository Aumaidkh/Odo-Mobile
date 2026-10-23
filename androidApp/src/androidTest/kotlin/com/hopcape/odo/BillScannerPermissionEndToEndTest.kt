package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
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
 * Declining no longer ends the scan: the gallery state follows, and only the X leaves.
 *
 * The system permission dialog itself is never tapped. It belongs to the OS, an instrumented
 * test cannot drive it reliably across versions, and what Odo owns is the screen in front of
 * it — which is the part that decides whether the prompt is ever seen with any context.
 */
@RunWith(AndroidJUnit4::class)
class BillScannerPermissionEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Put the device in the state each test needs **before** the activity launches.
     *
     * Where the app opens is decided once per launch and then held in saved state, so a
     * `@Before` is too late — the rule has already drawn a first frame against the previous
     * test's data. [DeviceState] runs outside the compose rule, so the seed lands first.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(DeviceState { startOnASetUpDeviceWithNoCameraPermission() })
        .around(rule)

    private fun startOnASetUpDeviceWithNoCameraPermission() {
        assumeFalse(
            "CAMERA is already granted for this install — uninstall the app and run this class first.",
            cameraPermissionGranted(),
        )
        resetScanner()
        seedOnboardedOwner()
        installDefaultScanning()
    }

    @Test
    fun theRationaleOffersTheCamera_theGallery_andNotNow() {
        rule.openScanner()

        rule.awaitText(ScanCopy.CAMERA_TITLE)
        rule.onNodeWithText(ScanCopy.CAMERA_SUBTITLE).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_ALLOW).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.GALLERY).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).assertIsDisplayed()
    }

    @Test
    fun theRationaleListsTheThreeTicks() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)

        rule.onNodeWithText(ScanCopy.CAMERA_TICK_PRINTED).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_TICK_PRIVATE).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.CAMERA_TICK_FAST).assertIsDisplayed()
    }

    @Test
    fun notNow_showsTheGalleryState_andTheScannerStaysOpen() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)

        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).performClick()

        rule.awaitText(ScanCopy.NO_CAMERA_TITLE)
        rule.onNodeWithText(ScanCopy.GALLERY).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.NO_CAMERA_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun onlyTheCloseButtonLeaves() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)
        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).performClick()
        rule.awaitText(ScanCopy.NO_CAMERA_TITLE)

        rule.onNodeWithLabel(ScanCopy.CLOSE_LABEL).performClick()

        rule.awaitLabel(ScanCopy.SCAN_ACTION)
        rule.onNodeWithText(ScanCopy.NO_CAMERA_TITLE).assertDoesNotExist()
    }

    @Test
    fun aGalleryPhoto_reachesReviewWithoutTheCamera() {
        Intents.init()
        try {
            installBillExtractor(readableBill())
            stubPickedImage()
            rule.openScanner()
            rule.awaitText(ScanCopy.CAMERA_TITLE)
            rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).performClick()
            rule.awaitText(ScanCopy.NO_CAMERA_TITLE)

            rule.onNodeWithText(ScanCopy.GALLERY).performClick()

            rule.awaitText(ScanCopy.REVIEW_TITLE, timeoutMillis = 10_000L)
        } finally {
            Intents.release()
        }
    }

    @Test
    fun noneOfTheViewfinderIsReachableWithoutTheCamera() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)
        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).performClick()
        rule.awaitText(ScanCopy.NO_CAMERA_TITLE)

        rule.onNodeWithLabel(ScanCopy.TORCH_ON).assertDoesNotExist()
        rule.onNodeWithText(ScanCopy.MODE_DOCUMENT).assertDoesNotExist()
        rule.onNodeWithText(ScanCopy.MANUAL).assertDoesNotExist()
    }
}
