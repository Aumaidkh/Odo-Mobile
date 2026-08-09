package com.hopcape.odo.core.platform.app

import android.content.Context
import android.os.Build

/**
 * Reads the version from the installed package, so it is whatever was actually shipped
 * rather than a string someone remembered to update.
 *
 * A package with no version name should not exist, but the field is nullable in the
 * platform API, so it falls back to [UNKNOWN_VERSION] instead of failing a screen.
 */
internal class AndroidAppInfo(private val context: Context) : AppInfo {

    private val packageInfo by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }

    override val versionName: String by lazy { packageInfo?.versionName ?: UNKNOWN_VERSION }

    // `longVersionCode` needs API 28; minSdk is 26, so the deprecated Int field is the
    // fallback below it. Both come from the same PackageInfo the OS assigned at install,
    // never a value the app makes up.
    override val versionCode: Long by lazy {
        val info = packageInfo ?: return@lazy UNKNOWN_VERSION_CODE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private companion object {
        const val UNKNOWN_VERSION = "—"
        const val UNKNOWN_VERSION_CODE = 0L
    }
}
