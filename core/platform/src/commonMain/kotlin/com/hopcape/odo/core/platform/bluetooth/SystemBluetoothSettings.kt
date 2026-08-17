package com.hopcape.odo.core.platform.bluetooth

/**
 * The one thing an app may do about a switched-off Bluetooth radio: send the owner to the
 * screen that switches it back on.
 *
 * Turning it on directly is not ours to do — Android has not let an app flip the radio since
 * API 33, and asking for that power to save one tap would be the wrong trade even where it
 * still works. So this seam opens the settings page and the owner decides.
 */
interface SystemBluetoothSettings {

    /**
     * Open the system's Bluetooth settings.
     *
     * @return whether anything opened. False on a build with no such screen to reach, and
     *   then the caller has to fall back to telling the owner where to look.
     */
    fun open(): Boolean
}
