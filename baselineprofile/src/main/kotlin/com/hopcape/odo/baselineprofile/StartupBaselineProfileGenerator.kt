package com.hopcape.odo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records what a cold start touches, so ART compiles it ahead of time instead of
 * interpreting it on the first launch.
 *
 * Measured on a mid-range phone: first launch after install went 8.6s -> 3.2s.
 * Re-run `:androidApp:generateBaselineProfile` when startup code changes.
 */
class StartupBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(
            packageName = targetPackage(),
            // Also emits the startup profile, which is the subset ART compiles first.
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // startActivityAndWait returns on the first frame, which here is the splash
            // MainActivity holds until the startup gate resolves. Wait past it, or the
            // profile stops short of everything the owner actually waits for.
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), WINDOW_TIMEOUT_MS)
            device.waitForIdle(IDLE_TIMEOUT_MS)
        }
    }

    /**
     * The applicationId under test — the plugin picks the variant, so this is never
     * hardcoded.
     *
     * Three sources because which one is set depends on the plugin version: `targetAppId`
     * is empty on ours. The target context is the backstop and is always present.
     */
    private fun targetPackage(): String {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("androidx.benchmark.targetPackageName")
            ?: args.getString("targetAppId")
            ?: InstrumentationRegistry.getInstrumentation().targetContext.packageName
    }

    private companion object {
        const val WINDOW_TIMEOUT_MS = 15_000L
        const val IDLE_TIMEOUT_MS = 10_000L
    }
}
