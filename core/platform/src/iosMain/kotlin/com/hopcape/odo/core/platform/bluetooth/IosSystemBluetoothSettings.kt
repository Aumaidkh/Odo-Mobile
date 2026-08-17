package com.hopcape.odo.core.platform.bluetooth

/**
 * iOS stub. Auto-odometer's Bluetooth trigger is Android-only for the MVP (CLAUDE.md), and
 * iOS has no equivalent page an app may open, so this reports that nothing was opened rather
 * than pretending otherwise.
 */
internal class IosSystemBluetoothSettings : SystemBluetoothSettings {
    override fun open(): Boolean = false
}
