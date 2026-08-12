package com.hopcape.odo.core.platform.app

import platform.Foundation.NSBundle

/** Reads the version from the app bundle's `CFBundleShortVersionString`/`CFBundleVersion`. */
internal class IosAppInfo : AppInfo {

    override val versionName: String by lazy {
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "—"
    }

    // CFBundleVersion is a build-number string, not necessarily numeric on iOS in general —
    // Odo's own build always sets it to the numeric build number, matching Android's
    // versionCode role. A non-numeric value falls back to 0, same as "cannot be read".
    override val versionCode: Long by lazy {
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toLongOrNull() ?: 0L
    }
}
