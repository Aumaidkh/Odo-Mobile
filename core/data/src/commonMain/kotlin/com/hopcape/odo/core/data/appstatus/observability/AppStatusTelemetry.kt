package com.hopcape.odo.core.data.appstatus.observability

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.currentTraceContext

/**
 * Observability for the app-availability gate, behind one intent-named surface — the same
 * shape as [com.hopcape.odo.core.data.observability.DataTelemetry] and
 * `SyncTelemetry`.
 *
 * [blocked]/[released] are the numbers that answer "how many installs did a maintenance
 * window actually stop" — emitted only on a change into or out of a
 * [com.hopcape.odo.core.domain.appstatus.AppAvailability.Blocked] state, never per
 * evaluation. `DegradedByMaintenance` is deliberately silent here: its product effect (sync
 * standing down) is `SyncTelemetry`'s own `skipped` event one layer up, so counting it again
 * here would double the same signal.
 */
internal class AppStatusTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
) {

    /** Spans a remote fetch. Never changes [block]'s result — fire-and-forget by contract. */
    suspend fun <T> refresh(block: suspend () -> T): T {
        val span = tracer.startSpan(name = "$TAG.refresh", traceId = currentTraceContext().traceId ?: UNTRACED)
        return try {
            block()
        } finally {
            tracer.endSpan(span)
        }
    }

    /** The remote source could not be read — never a throw, so this is a log, not a crash. */
    fun fetchFailed() = logger.warn(TAG, EVENT_FETCH_FAILED)

    /** The app just transitioned into a [com.hopcape.odo.core.domain.appstatus.AppAvailability.Blocked] state. */
    fun blocked(reason: String) {
        logger.info(TAG, EVENT_BLOCKED, fields = mapOf(Key.REASON to reason))
        analytics.track(EVENT_BLOCKED, mapOf(Key.REASON to reason))
    }

    /** The app just left a [com.hopcape.odo.core.domain.appstatus.AppAvailability.Blocked] state. */
    fun released() {
        logger.info(TAG, EVENT_RELEASED)
        analytics.track(EVENT_RELEASED)
    }

    internal companion object {
        const val TAG = "AppStatus"
        const val EVENT_FETCH_FAILED = "app_status_fetch_failed"
        const val EVENT_BLOCKED = "app_status_blocked"
        const val EVENT_RELEASED = "app_status_released"
        const val UNTRACED = "untraced"

        /** [Key.REASON] values for [blocked]. */
        const val REASON_UPDATE_REQUIRED = "update_required"
        const val REASON_MAINTENANCE = "maintenance"
    }

    private object Key {
        const val REASON = "reason"
    }
}
