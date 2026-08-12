@file:OptIn(ExperimentalUuidApi::class)

package com.hopcape.crashreporting.internal.model

import com.hopcape.crashreporting.api.DeviceContext
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ─────────────────────────────────────────────────────────────
// CrashReport — immutable, fully-resolved crash ready for the
// redactor, file store, and destinations. The analog of the
// analytics AnalyticsEvent / APM CompletedSpan; it never crosses
// the public boundary.
//
// The Throwable is captured as three plain strings (type / message /
// stack) rather than held live: a report may be serialized to disk
// and re-read on the next launch, long after the original Throwable
// (and its heap) is gone. `crashId` is the on-disk filename and the
// dedup/delivery key; `isFatal` distinguishes an uncaught crash from
// a handled exception. `traceId`/`sessionId` correlate a crash with
// the Logger / Analytics / APM records from the same session.
// ─────────────────────────────────────────────────────────────
internal data class CrashReport(
    val crashId: String,
    val timestampMs: Long,
    val throwableType: String,
    val throwableMessage: String?,
    val stackTrace: String,
    val isFatal: Boolean,
    val breadcrumbs: List<Breadcrumb>,
    val customKeys: Map<String, Any?>,
    val deviceContext: DeviceContext,
    val traceId: String? = null,
    val sessionId: String? = null,
) {
    companion object {
        /**
         * Builds a report from a live [throwable] at capture time, generating a
         * fresh [crashId] and wall-clock timestamp. Splitting the throwable into
         * strings here is what lets the report outlive the process.
         */
        fun from(
            throwable: Throwable,
            isFatal: Boolean,
            breadcrumbs: List<Breadcrumb>,
            customKeys: Map<String, Any?>,
            deviceContext: DeviceContext,
            traceId: String? = null,
            sessionId: String? = null,
        ): CrashReport = CrashReport(
            crashId = Uuid.random().toString(),
            timestampMs = Clock.System.now().toEpochMilliseconds(),
            throwableType = throwable::class.simpleName ?: "Throwable",
            throwableMessage = throwable.message,
            stackTrace = throwable.stackTraceToString(),
            isFatal = isFatal,
            breadcrumbs = breadcrumbs,
            customKeys = customKeys,
            deviceContext = deviceContext,
            traceId = traceId,
            sessionId = sessionId,
        )
    }
}
