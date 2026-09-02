package com.hopcape.odo.feature.onboarding.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.performance.api.TraceContext as LogTraceSource
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for the first-run pitch — the Welcome screen and the video intro.
 *
 * The setup steps have their own facade in `:feature:questionnaire`, with its own flow id.
 * The two funnels are joined by the session rather than by one trace: keeping a single trace
 * across both modules would need a shared seam in `:core:*` that nothing else wants.
 *
 * A Koin `factory`, so one instance covers one visit to the pitch.
 */
internal class OnboardingTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    ids: IdGenerator,
) {

    private val flowTrace = PerfTrace(flowId = FLOW, traceId = "${FLOW}_${ids.newId()}")

    fun welcomeShown() {
        analytics.track(Event.WELCOME_SHOWN)
        logger.info(TAG, Event.WELCOME_SHOWN, tc = flowTrace.toLog())
    }

    fun welcomeCompleted() {
        analytics.track(Event.WELCOME_COMPLETED)
        logger.info(TAG, Event.WELCOME_COMPLETED, tc = flowTrace.toLog())
    }

    /** Which legal page was opened — the only property is *which*, never who opened it. */
    fun legalOpened(document: String) {
        analytics.track(Event.LEGAL_OPENED, mapOf(Key.DOCUMENT to document))
        logger.debug(TAG, Event.LEGAL_OPENED, tc = flowTrace.toLog(), fields = mapOf(Key.DOCUMENT to document))
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "ONBOARDING"
        const val FLOW = "onboarding"
    }

    /** Event names are shipped contracts: reuse one rather than inventing a synonym. */
    object Event {
        const val WELCOME_SHOWN = "onboarding_welcome_shown"
        const val WELCOME_COMPLETED = "onboarding_welcome_completed"
        const val LEGAL_OPENED = "onboarding_legal_opened"
    }

    object Key {
        const val DOCUMENT = "document"
    }
}
