package com.hopcape.odo.core.platform.bluetooth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/** Asks the system to switch the phone's Bluetooth radio on, for the screen that holds it. */
@Stable
interface BluetoothEnabler {

    /**
     * Ask for the radio.
     *
     * Deliberately returns nothing and reports nothing back. Whether the radio actually came
     * on is read from `BluetoothRadio.enabled`, which is the truth either way — the owner can
     * say no to the dialog and then switch it on from the notification shade a second later,
     * and an answer captured here would already be wrong by then.
     */
    fun request()
}

/**
 * The enabler for the current screen.
 *
 * A composable rather than an injected port, for the same reason
 * [com.hopcape.odo.core.platform.permission.rememberPermissionController] is one: this puts a
 * system dialog on screen, which needs the thing hosting the UI, and no Koin singleton can
 * hold an Activity without leaking it.
 */
@Composable
expect fun rememberBluetoothEnabler(): BluetoothEnabler
