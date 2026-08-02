package com.hopcape.odo.core.platform.app

import android.content.Context

/**
 * Reads the version from the installed package, so it is whatever was actually shipped
 * rather than a string someone remembered to update.
 *
 * A package with no version name should not exist, but the field is nullable in the
 * platform API, so it falls back to [UNKNOWN_VERSION] instead of failing a screen.
 */
internal class AndroidAppInfo(private val context: Context) : AppInfo {

    override val versionName: String by lazy {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: UNKNOWN_VERSION
    }

    private companion object {
        const val UNKNOWN_VERSION = "—"
    }
}
