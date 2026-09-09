package com.hopcape.odo

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Where a saved scan lands while `bill_check_enabled` is closed — the shipped default.
 *
 * [BillScannerEndToEndTest] pins the flag open, so the saved-success screen only exists in
 * this file. Its own suite is what the flag costs: the value is read once per launch.
 *
 * The success screen is terminal. It used to be left under whatever it opened, so back from
 * the service log stepped into a success screen the owner had already finished with, with the
 * viewfinder still behind that.
 */
@RunWith(AndroidJUnit4::class)
class BillSaveSuccessEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        // Closed, as it ships. This is the path the flag-off build actually takes.
        .outerRule(PinnedConfig("bill_check_enabled", value = "false", compiledDefault = "false"))
        .around(DeviceState { startOnASetUpDeviceWithNothingScanned() })
        .around(rule)

    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private fun startOnASetUpDeviceWithNothingScanned() {
        Intents.init()
        resetScanner()
        seedOnboardedOwner()
        seedCapturedPhoto()
        installDefaultScanning()
    }

    @After
    fun tearDown() = Intents.release()

    @Before
    fun readableBillInFrontOfTheExtractor() = installBillExtractor(readableBill())

    /** Saves a scanned bill and stops on the success screen. */
    private fun saveAScannedBill() {
        rule.openScanner()
        rule.openBillReview()
        rule.awaitText(ScanCopy.REVIEW_TITLE)
        rule.awaitGone(ScanCopy.READING)

        rule.onNodeWithText(ScanCopy.REVIEW_SAVE).performClick()

        rule.awaitText(ScanCopy.SAVE_TITLE, timeoutMillis = 10_000L)
    }

    @Test
    fun withTheBillCheckClosed_aSavedBillLandsOnTheSuccessScreen() {
        saveAScannedBill()

        rule.onNodeWithText(ScanCopy.SAVE_VIEW).assertIsDisplayed()
    }

    @Test
    fun backFromTheServiceLogLeavesTheFinishedScanBehind() {
        saveAScannedBill()

        rule.onNodeWithText(ScanCopy.SAVE_VIEW).performClick()
        rule.awaitText(LogCopy.LIST_TITLE)

        Espresso.pressBack()

        // Home, which is where the scan started — not the success screen, and not the
        // viewfinder that was under it.
        rule.awaitText(HomeCopy.HEALTH_SCORE)
        rule.onNodeWithText(ScanCopy.SAVE_TITLE).assertDoesNotExist()
    }

    /** The same, for the plain dismiss — the two ways off the screen end in the same place. */
    @Test
    fun backAfterDoneLeavesTheFinishedScanBehind() {
        saveAScannedBill()

        rule.onNodeWithText(ScanCopy.SAVE_DONE).performClick()
        rule.awaitText(LogCopy.LIST_TITLE)

        Espresso.pressBack()

        rule.awaitText(HomeCopy.HEALTH_SCORE)
        rule.onNodeWithText(ScanCopy.SAVE_TITLE).assertDoesNotExist()
    }
}
