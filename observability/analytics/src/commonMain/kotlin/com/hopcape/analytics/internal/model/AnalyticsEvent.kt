@file:OptIn(ExperimentalUuidApi::class)

package com.hopcape.analytics.internal.model

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ─────────────────────────────────────────────────────────────
// AnalyticsEvent — immutable, fully-resolved event ready for
// dispatch. `eventId` is the delivery/dead-letter key inside the
// store & dispatcher; `sequenceNumber` preserves ordering for
// funnel-sensitive backends. Internal: it never crosses the public
// boundary — callers only ever pass a name + properties map.
// ─────────────────────────────────────────────────────────────
internal data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, Any?>,
    val context: GlobalContext,
    val sequenceNumber: Long = 0L,
    val eventId: String = Uuid.random().toString(),
    val timestampMs: Long = Clock.System.now().toEpochMilliseconds(),
)
