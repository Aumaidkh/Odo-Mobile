package com.hopcape.odo.splash

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.domain.settings.model.ThemePreference

/**
 * Hands Odo's own Light / Dark / System choice to the platform, so the system splash
 * draws in the palette the app will open in. Android 12+ only: older versions offer no
 * per-app night mode, and their splash follows the phone's setting.
 */
internal object SplashNightMode {

    fun apply(context: Context, theme: ThemePreference) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode = when (theme) {
            ThemePreference.DARK -> UiModeManager.MODE_NIGHT_YES
            ThemePreference.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemePreference.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        }
        try {
            context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode)
        } catch (e: RuntimeException) {
            // A wrong splash palette is cosmetic; it must never break startup.
            HLogger.tag(TAG).w("splash_night_mode_failed", mapOf("theme" to theme.name, "cause" to e::class.simpleName))
        }
    }

    private const val TAG = "SPLASH"
}
