package com.hopcape.odo

import android.graphics.Bitmap
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

    /** Capture the screen as `<name>.png`, replacing any earlier take. */
    fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Let the frame settle: a sheet that is still animating photographs half-open.
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
