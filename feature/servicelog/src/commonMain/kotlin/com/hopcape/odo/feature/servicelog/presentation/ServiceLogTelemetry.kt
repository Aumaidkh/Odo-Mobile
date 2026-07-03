package com.hopcape.odo.feature.servicelog.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator

/**
 * Observability for the service-log flows, behind intent-named methods so the
 * ViewModels read as pure logic. Analytics + logs only (no spans — no timed work
 * here), mirroring `WelcomeTelemetry`. A `factory` in Koin, so each ViewModel
 * instance mints its own per-flow `traceId` under one shared `flowId`.
 *
 * No PII: amounts, odometer readings, workshop names and notes never leave the
 * device via telemetry — only counts, flags, and error categories.
 */
internal class ServiceLogTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    ids: IdGenerator,
) {
    private val flowTrace = TraceContext(flowId = FLOW, traceId = "servicelog_${ids.newId()}")

    fun listViewed(count: Int, verified: Int) {
        val fields = mapOf(Key.COUNT to count, Key.VERIFIED to verified)
        analytics.track(Event.LIST_VIEWED, fields)
        logger.info(TAG, "list_viewed", tc = flowTrace, fields = fields)
    }

    fun formOpened(isEdit: Boolean) {
        val fields = mapOf(Key.IS_EDIT to isEdit)
        analytics.track(Event.FORM_OPENED, fields)
        logger.info(TAG, "form_opened", tc = flowTrace, fields = fields)
    }

    fun saved(isEdit: Boolean, verified: Boolean) {
        val fields = mapOf(Key.IS_EDIT to isEdit, Key.VERIFIED to verified)
        analytics.track(Event.SAVED, fields)
        logger.info(TAG, "log_saved", tc = flowTrace, fields = fields)
    }

    fun saveFailed(reason: String) {
        val fields = mapOf(Key.REASON to reason)
        analytics.track(Event.SAVE_FAILED, fields)
        logger.warn(TAG, "log_save_failed", tc = flowTrace, fields = fields)
    }

    fun deleted() {
        analytics.track(Event.DELETED)
        logger.info(TAG, "log_deleted", tc = flowTrace)
    }

    companion object {
        const val TAG = "SERVICE_LOG"
        const val FLOW = "servicelog"

        const val REASON_VALIDATION = "validation"
        const val REASON_PERSISTENCE = "persistence"
    }

    object Event {
        const val LIST_VIEWED = "service_log_list_viewed"
        const val FORM_OPENED = "service_log_form_opened"
        const val SAVED = "service_log_saved"
        const val SAVE_FAILED = "service_log_save_failed"
        const val DELETED = "service_log_deleted"
    }

    object Key {
        const val COUNT = "count"
        const val VERIFIED = "verified"
        const val IS_EDIT = "is_edit"
        const val REASON = "reason"
    }
}
