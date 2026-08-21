package com.hopcape.odo.core.platform.app

import android.os.Build

/**
 * Reads the model and OS version from `Build`.
 *
 * The API level is included next to the release name because they do not always move
 * together on the phones that report oddly, and the API level is the one that explains a
 * behaviour difference.
 */
internal class AndroidDeviceInfo : DeviceInfo {

    override val manufacturer: String = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: UNKNOWN

    override val model: String = Build.MODEL?.takeIf { it.isNotBlank() } ?: UNKNOWN

    override val osVersion: String =
        "Android ${Build.VERSION.RELEASE ?: UNKNOWN} (API ${Build.VERSION.SDK_INT})"

    private companion object {
        const val UNKNOWN = "—"
    }
}
