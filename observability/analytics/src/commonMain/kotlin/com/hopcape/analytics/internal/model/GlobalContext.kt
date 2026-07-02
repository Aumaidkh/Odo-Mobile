@file:OptIn(ExperimentalUuidApi::class)

package com.hopcape.analytics.internal.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ─────────────────────────────────────────────────────────────
// GlobalContext — "super properties" auto-attached to every event
// without call sites repeating them. Immutable snapshot, rebuilt
// when app/session state changes (login, app version bump, etc.).
// Internal: callers configure it via AnalyticsConfig + the facade,
// never by constructing this directly.
// ─────────────────────────────────────────────────────────────
internal data class GlobalContext(
    val appVersion: String,
    val platform: String = "android",
    val deviceModel: String,
    val osVersion: String,
    val locale: String,
    val sessionId: String? = null,
    val anonymousId: String = Uuid.random().toString(),
    val userId: String? = null,
)
