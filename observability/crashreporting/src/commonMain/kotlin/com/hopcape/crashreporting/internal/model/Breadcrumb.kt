package com.hopcape.crashreporting.internal.model

// ─────────────────────────────────────────────────────────────
// Breadcrumb — one entry in the crash trail: a timestamped note of
// something that happened before the crash ("login_attempt_started",
// "ble_connected"). Internal: it only ever appears inside a
// CrashReport, which never crosses the public boundary.
// ─────────────────────────────────────────────────────────────
internal data class Breadcrumb(
    val timestampMs: Long,
    val tag: String,
    val message: String,
)
