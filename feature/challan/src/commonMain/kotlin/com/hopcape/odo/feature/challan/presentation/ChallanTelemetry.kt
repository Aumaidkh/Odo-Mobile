package com.hopcape.odo.feature.challan.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger

/**
 * All observability for the challans feature, behind intent-named methods, so the
 * ViewModels read as their screens' logic.
 *
 * **No PII.** Counts, booleans and error type names only — never a registration number: a
 * plate identifies a person's vehicle, and the lookup screen exists precisely for plates
 * that are not the owner's.
 */
internal class ChallanTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
) {

    fun listOpened(pendingCount: Int, courtCount: Int) {
        analytics.track(Event.LIST_OPENED, mapOf(Key.PENDING to pendingCount, Key.COURT to courtCount))
    }

    fun refreshed(succeeded: Boolean) {
        analytics.track(Event.REFRESHED, mapOf(Key.SUCCESS to succeeded))
    }

    fun payTapped(pendingCount: Int) {
        analytics.track(Event.PAY_TAPPED, mapOf(Key.PENDING to pendingCount))
    }

    fun markedPaid(pendingCount: Int) {
        analytics.track(Event.MARKED_PAID, mapOf(Key.PENDING to pendingCount))
    }

    fun lookupSubmitted() {
        analytics.track(Event.LOOKUP_SUBMITTED, emptyMap())
    }

    /** [outcome] is one of `found` / `clean` / `not_found` / `unreachable`. */
    fun lookupAnswered(outcome: String) {
        analytics.track(Event.LOOKUP_ANSWERED, mapOf(Key.OUTCOME to outcome))
    }

    fun readFailed(screen: String, cause: Throwable) {
        logger.error(TAG, "$screen.read_failed", fields = mapOf(Key.ERROR to (cause::class.simpleName ?: "Unknown")))
    }

    object Event {
        const val LIST_OPENED = "challan_list_opened"
        const val REFRESHED = "challan_refreshed"
        const val PAY_TAPPED = "challan_pay_tapped"
        const val MARKED_PAID = "challan_marked_paid"
        const val LOOKUP_SUBMITTED = "challan_lookup_submitted"
        const val LOOKUP_ANSWERED = "challan_lookup_answered"
    }

    object Key {
        const val PENDING = "pending_count"
        const val COURT = "court_count"
        const val SUCCESS = "success"
        const val OUTCOME = "outcome"
        const val ERROR = "error"
    }

    private companion object {
        const val TAG = "challan"
    }

    object Screen {
        const val LIST = "list"
        const val LOOKUP = "lookup"
        const val RESULT = "result"
    }
}
