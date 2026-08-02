package com.hopcape.odo.core.platform.app

import platform.Foundation.NSBundle

/** Reads the version from the app bundle's `CFBundleShortVersionString`. */
internal class IosAppInfo : AppInfo {

    override val versionName: String by lazy {
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "—"
    }
}
