package com.hopcape.odo.feature.support.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.logging.api.TraceContext as LogTrace

/**
 * All observability for the ticket forms, behind intent-named methods.
 *
 * **Nothing the owner wrote is emitted.** Not the body, not the address, not a file name — a
 * support ticket is where somebody explains their problem in their own words, and those words
 * are the last thing that belongs on a dashboard. What is worth counting is the shape: which
 * kind was sent, whether it carried anything, and what failed.
 */
internal class SupportTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
) {

    /**
     * Time a send.
     *
     * Worth a span because it is the slowest thing this feature does and the one an owner
     * waits on: copying every attachment into app storage, then a database write. A report
     * that takes four seconds to save looks broken, and only the number tells us which half.
     */
    suspend fun <T> timingSubmit(block: suspend () -> T): T {
        val span = tracer.startSpan(Trace.SUBMIT, FLOW)
        return try {
            block()
        } finally {
            tracer.endSpan(span)
        }
    }

    /**
     * A ticket was saved. [attachments] and [logsAttached] are counts and flags, never names.
     *
     * Counted at the save rather than at the tap: a tap that failed validation is not a
     * submission, and counting it would put a number on the dashboard nobody can act on.
     */
    fun ticketSubmitted(kind: TicketKind, attachments: Int, logsAttached: Boolean) {
        val fields = mapOf(
            Key.KIND to kind.name,
            Key.ATTACHMENTS to attachments,
            Key.LOGS to logsAttached,
        )
        analytics.track(Event.TICKET_SUBMITTED, fields)
        logger.info(TAG, Event.TICKET_SUBMITTED, tc = trace, fields = fields)
    }

    /**
     * The save failed, which means the ticket is nowhere — not on the device, not queued.
     *
     * The error's type name only. A validation failure's own data is the thing that failed
     * it, and here that is the owner's message.
     */
    fun submitFailed(kind: TicketKind, error: Any) {
        logger.error(
            TAG,
            Event.SUBMIT_FAILED,
            tc = trace,
            fields = mapOf(
                Key.KIND to kind.name,
                Key.ERROR to (error::class.simpleName ?: UNKNOWN),
            ),
        )
    }

    /** A vote is the cheapest signal this feature produces about what people want. */
    fun ideaVoted(voted: Boolean) {
        val fields = mapOf(Key.VOTED to voted)
        analytics.track(Event.IDEA_VOTED, fields)
        logger.info(TAG, Event.IDEA_VOTED, tc = trace, fields = fields)
    }

    /**
     * The vote was not written.
     *
     * Worth a line of its own: the pill snapping back looks identical to a double tap, so
     * without this a vote that never saved is indistinguishable from one the owner undid.
     */
    fun voteFailed(error: Any) {
        logger.warn(
            TAG,
            Event.VOTE_FAILED,
            tc = trace,
            fields = mapOf(Key.ERROR to (error::class.simpleName ?: UNKNOWN)),
        )
    }

    /** The list could not be refreshed. Not shown to the owner — what is cached still is. */
    fun ideasRefreshFailed(error: Any) {
        logger.warn(
            TAG,
            Event.IDEAS_REFRESH_FAILED,
            tc = trace,
            fields = mapOf(Key.ERROR to (error::class.simpleName ?: UNKNOWN)),
        )
    }

    private val trace = LogTrace(flowId = FLOW)

    private companion object {
        const val TAG = "SUPPORT"
        const val FLOW = "support"
        const val UNKNOWN = "Unknown"
    }

    /*
     * The feature's observability taxonomy. These names are what a dashboard queries, so
     * treat them as shipped contracts.
     */

    object Event {
        const val TICKET_SUBMITTED = "support_ticket_submitted"
        const val SUBMIT_FAILED = "support_submit_failed"
        const val IDEA_VOTED = "support_idea_voted"
        const val IDEAS_REFRESH_FAILED = "support_ideas_refresh_failed"
        const val VOTE_FAILED = "support_vote_failed"
    }

    object Trace {
        const val SUBMIT = "support_submit"
    }

    object Key {
        const val KIND = "kind"
        const val ATTACHMENTS = "attachments"
        const val LOGS = "logs"
        const val VOTED = "voted"
        const val ERROR = "error"
    }
}
