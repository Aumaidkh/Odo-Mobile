package com.hopcape.odo.core.platform.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual — `ACTION_REQUEST_ENABLE`, the system's own "allow Odo to turn on Bluetooth?"
 * dialog. One tap, and the owner never leaves Odo.
 *
 * The result code is thrown away on purpose. `BluetoothRadio.enabled` is what the screen reads,
 * and it reports the radio itself rather than what the owner said about it a moment ago — so
 * Allow, Deny, and "denied here but switched on from the shade afterwards" all resolve through
 * one path instead of three. A launcher is still used rather than a bare `startActivity` so the
 * dialog belongs to the Activity that asked for it.
 *
 * Falls back to the system Bluetooth page if the enable intent is refused — a few OEM builds
 * restrict it. The rule this keeps is the one
 * [com.hopcape.odo.core.platform.permission.rememberPermissionController] keeps: a tap on this
 * never does nothing visible.
 *
 * `ACTION_REQUEST_ENABLE` needs `BLUETOOTH_CONNECT` on API 31+. Every screen that can reach
 * this is already past its permission gate, and the `SecurityException` catch covers the case
 * where the OS revoked it in between.
 */
@Composable
actual fun rememberBluetoothEnabler(): BluetoothEnabler {
    val context = LocalContext.current

    // Held across recompositions for the same reason the permission launchers are: a contract
    // built inline re-registers its callback on every recomposition, and a result arriving
    // mid-swap is dropped.
    val contract = remember { ActivityResultContracts.StartActivityForResult() }
    val launcher = rememberLauncherForActivityResult(contract) { /* see KDoc: the radio is read, not this. */ }

    return remember(context, launcher) {
        object : BluetoothEnabler {
            override fun request() {
                try {
                    launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (e: ActivityNotFoundException) {
                    context.openBluetoothSettings()
                } catch (e: SecurityException) {
                    context.openBluetoothSettings()
                }
            }
        }
    }
}

/** The last resort: the phone's own Bluetooth page, where the switch always exists. */
private fun Context.openBluetoothSettings() {
    val settings = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(settings)
    } catch (e: ActivityNotFoundException) {
        // A phone with neither the enable dialog nor a Bluetooth settings page. Nothing left
        // to try; the screen keeps saying the radio is off, which stays true.
    }
}
