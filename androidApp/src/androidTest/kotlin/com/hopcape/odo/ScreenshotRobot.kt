package com.hopcape.odo

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Captures what is on screen, for the Before/After table a UI change has to carry into its PR.
 *
 * Taken through the instrumented harness rather than by hand with `adb screencap`, because a
 * local debug build has no RevenueCat key and no Supabase: driven by hand, a paid or synced
 * surface photographs its error state instead of the feature. The tests seed the owner and
 * inject the store's answers, so what lands here is what an owner sees.
 *
 * The whole device screen, not the Compose root — a bottom sheet is drawn over a scrim that
 * belongs in the picture, and the status bar is what makes a screenshot look like a phone.
 *
 * Files go to the app's own external directory, which needs no permission and survives the
 * test process, ready for `adb pull`. See `.github/screenshots/README.md`.
 */
internal object Screenshots {

    private val directory: File by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    }

    /**
     * Capture the screen as `<name>.png`, replacing any earlier take.
     *
     * Prefer [captureScreen], which waits for Compose first. This one only waits for the main
     * thread to go quiet, which a running sheet animation does not count as — a sheet caught
     * mid-slide photographs the screen behind it.
     */
    fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("the device returned no screenshot for '$name'")
        File(directory, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, out)
        }
        bitmap.recycle()
    }

    /** PNG is lossless, so this is ignored — passed because the API requires it. */
    private const val QUALITY = 100
}

/**
 * Wait for the screen to stop moving, then capture it as `<name>.png`.
 *
 * `waitForIdle` covers the Compose animation — a bottom sheet's slide in particular, which
 * finding its text does not mean has finished. The settle after it covers the window's own
 * scrim fade, which Compose does not know about and no idling resource reports.
 */
internal fun ComposeTestRule.captureScreen(name: String) {
    waitForIdle()
    SystemClock.sleep(SETTLE_MILLIS)
    Screenshots.capture(name)
}

private const val SETTLE_MILLIS = 400L
