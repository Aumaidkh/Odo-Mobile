package com.hopcape.performance.internal.model

// ─────────────────────────────────────────────────────────────
// SpanContext — device/app/session metadata auto-attached to every
// completed span without call sites repeating it. Immutable
// snapshot, rebuilt when session state changes (setSession). Mirrors
// the analytics module's GlobalContext. Internal: callers configure
// it via PerformanceConfig + the facade, never by constructing this.
// ─────────────────────────────────────────────────────────────
internal data class SpanContext(
    val appVersion: String,
    val platform: String = "android",
    val deviceModel: String,
    val osVersion: String,
    val locale: String,
    val sessionId: String? = null,
)
