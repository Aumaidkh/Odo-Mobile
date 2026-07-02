package com.hopcape.odo.feature.onboarding.presentation.welcome

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry.Event
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry.Key

private const val TAG = OnboardingTelemetry.TAG
private const val FLOW = OnboardingTelemetry.FLOW

/**
 * Observability for the intro carousel, behind intent-named methods so
 * [WelcomeViewModel] reads as pure carousel logic. Simpler than its setup sibling —
 * analytics + logs only, no spans (the carousel has no timed work), and it uses the
 * logging module's plain [TraceContext] directly (no coroutine propagation needed).
 *
 * It reuses the shared [com.hopcape.odo.feature.onboarding.presentation.OnboardingTelemetry]
 * taxonomy and [FLOW], so its lines correlate with the setup flow under one `flowId`
 * while carrying their own per-attempt `traceId`.
 */
internal class WelcomeTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    ids: IdGenerator,
) {

    /** The intro's correlation trace, minted once per carousel instance. */
    private val flowTrace = TraceContext(flowId = FLOW, traceId = ids.newId())

    fun welcomeShown() {
        analytics.track(Event.WELCOME_SHOWN)
        logger.info(TAG, "welcome_shown", tc = flowTrace)
    }

    fun slideViewed(page: WelcomePage, index: Int) {
        val fields = mapOf(Key.SLIDE to page.name, Key.SLIDE_INDEX to index)
        analytics.track(Event.WELCOME_SLIDE_VIEWED, fields)
        logger.debug(TAG, "welcome_slide_viewed", tc = flowTrace, fields = fields)
    }

    fun skipped(from: WelcomePage) {
        analytics.track(Event.WELCOME_SKIPPED, mapOf(Key.FROM_SLIDE to from.name))
        logger.info(TAG, "welcome_skipped", tc = flowTrace, fields = mapOf(Key.FROM_SLIDE to from.name))
    }

    fun completed() {
        analytics.track(Event.WELCOME_COMPLETED)
        logger.info(TAG, "welcome_completed", tc = flowTrace)
    }
}
