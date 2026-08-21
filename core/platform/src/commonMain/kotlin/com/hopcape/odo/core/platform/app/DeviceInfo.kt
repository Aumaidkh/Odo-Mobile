package com.hopcape.odo.core.platform.app

/**
 * What phone Odo is running on.
 *
 * Separate from [AppInfo], which answers what build this is. Both end up in the footer of a
 * support mail, and they come from different places: the build from the installed package,
 * the phone from the OS.
 *
 * Only what helps reproduce a report. No advertising ID, no serial number, nothing that
 * identifies the device rather than the model — this text is shown to the owner in the
 * draft before they send it, and it has to be something they are willing to send.
 */
interface DeviceInfo {

    /** Who made the phone, e.g. `Samsung`. `—` when it cannot be read. */
    val manufacturer: String

    /** The model, e.g. `SM-G991B`. `—` when it cannot be read. */
    val model: String

    /** The OS and its version, e.g. `Android 14 (API 34)` or `iOS 17.2`. */
    val osVersion: String
}
