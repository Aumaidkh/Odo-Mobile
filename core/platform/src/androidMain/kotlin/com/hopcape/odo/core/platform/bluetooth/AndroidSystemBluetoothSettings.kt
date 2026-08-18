package com.hopcape.odo.core.platform.bluetooth

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * [SystemBluetoothSettings] via `Settings.ACTION_BLUETOOTH_SETTINGS`, with the app's own
 * settings page as the fallback — a build that ships no Bluetooth screen at that action
 * still gets the owner somewhere they can act, rather than a button that does nothing.
 */
internal class AndroidSystemBluetoothSettings(
    private val context: Context,
) : SystemBluetoothSettings {

    override fun open(): Boolean {
        val bluetooth = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(bluetooth) }.isSuccess) return true

        val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(appSettings) }.isSuccess
    }
}
