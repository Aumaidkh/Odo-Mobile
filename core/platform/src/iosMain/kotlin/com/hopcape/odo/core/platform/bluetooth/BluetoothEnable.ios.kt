package com.hopcape.odo.core.platform.bluetooth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS actual — does nothing.
 *
 * iOS gives an app no way to switch the radio on, and no way to send the owner to the switch
 * either: saying so and waiting is the whole of what is allowed. The auto-odometer feature is
 * Android-only for the MVP (CLAUDE.md), so nothing calls this yet.
 */
@Composable
actual fun rememberBluetoothEnabler(): BluetoothEnabler = remember {
    object : BluetoothEnabler {
        override fun request() = Unit
    }
}
