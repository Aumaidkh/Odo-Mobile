package com.hopcape.crashreporting.api

// ─────────────────────────────────────────────────────────────
// DeviceContext — device/app/runtime snapshot attached to every
// crash report, so a dashboard entry carries the state the app was
// in when it died. Public because the host app is the only thing
// that can read the *dynamic* fields (free memory, battery, network)
// at capture time; it supplies them through
// CrashConfig.deviceContextProvider.
//
// The provider is invoked LAZILY, at the moment of a crash — so the
// values reflect the state right before death, not app startup.
// ─────────────────────────────────────────────────────────────
data class DeviceContext(
    val appVersion: String,
    val osVersion: String,
    val deviceModel: String,
    val platform: String = "android",
    /** Free heap/RAM in MB at capture time; `-1` when the app can't measure it. */
    val availableMemoryMb: Long = -1,
    /** Battery level 0..100 at capture time; `-1` when unknown. */
    val batteryLevel: Int = -1,
    /** Coarse network type ("wifi" / "cellular" / "none" / "unknown"). */
    val networkType: String = "unknown",
)
