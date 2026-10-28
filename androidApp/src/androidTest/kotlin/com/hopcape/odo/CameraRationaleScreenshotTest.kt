package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The After pictures for the camera rationale (S5) and the gallery state (S6). Fresh install only. */
@RunWith(AndroidJUnit4::class)
class CameraRationaleScreenshotTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                assumeFalse("CAMERA is already granted for this install.", cameraPermissionGranted())
                resetScanner()
                seedOnboardedOwner()
                installDefaultScanning()
                silenceTheCoachMarks()
            },
        )
        .around(rule)

    @Test
    fun capturesTheRationaleAndTheGalleryState() {
        rule.openScanner()
        rule.awaitText(ScanCopy.CAMERA_TITLE)
        rule.captureScreen("after-s5-camera-rationale")

        rule.onNodeWithText(ScanCopy.CAMERA_NOT_NOW).performClick()
        rule.awaitText(ScanCopy.NO_CAMERA_TITLE)
        rule.captureScreen("after-s6-gallery-state")
    }
}
