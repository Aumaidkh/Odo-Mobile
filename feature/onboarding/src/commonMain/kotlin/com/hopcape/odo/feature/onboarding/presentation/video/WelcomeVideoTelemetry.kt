package com.hopcape.odo.feature.onboarding.presentation.video

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for the video intro.
 *
 * It shipped with none, so on a build where the video flag is on the first screen of first
 * run is invisible: installs appear at `onboarding_started` with nothing before it.
 *
 * Shares [FLOW] with `OnboardingTelemetry` so the pitch reads as one funnel whichever intro
 * served it. A Koin `factory`, so one instance covers one visit.
 */
internal class WelcomeVideoTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    ids: IdGenerator,
) {

    private val flowTrace = PerfTrace(flowId = FLOW, traceId = "${FLOW}_${ids.newId()}")

    /** [pages] is how many the config built — zero clips still renders two pages of copy. */
    fun shown(pages: Int) {
        analytics.track(Event.SHOWN, mapOf(Key.PAGES to pages))
        logger.info(TAG, Event.SHOWN, tc = flowTrace.toLog(), fields = mapOf(Key.PAGES to pages))
    }

    fun pageViewed(page: Int) {
        analytics.track(Event.PAGE_VIEWED, mapOf(Key.PAGE to page))
        logger.debug(TAG, Event.PAGE_VIEWED, tc = flowTrace.toLog(), fields = mapOf(Key.PAGE to page))
    }

    /** Which page they bailed on is the whole value here — page 0 and page 1 mean different things. */
    fun skipped(page: Int) {
        analytics.track(Event.SKIPPED, mapOf(Key.PAGE to page))
        logger.info(TAG, Event.SKIPPED, tc = flowTrace.toLog(), fields = mapOf(Key.PAGE to page))
    }

    fun completed() {
        analytics.track(Event.COMPLETED)
        logger.info(TAG, Event.COMPLETED, tc = flowTrace.toLog())
    }

    /**
     * A clip never arrived. Not an error the owner sees — the page keeps its copy and its
     * button — but the rate says whether the clips are worth streaming at all.
     */
    fun clipFailed(page: Int) {
        analytics.track(Event.CLIP_FAILED, mapOf(Key.PAGE to page))
        logger.info(TAG, Event.CLIP_FAILED, tc = flowTrace.toLog(), fields = mapOf(Key.PAGE to page))
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    private companion object {
        const val TAG = "ONBOARDING"
        const val FLOW = "onboarding"
    }

    /** Event names are shipped contracts: reuse one rather than inventing a synonym. */
    object Event {
        const val SHOWN = "onboarding_video_shown"
        const val PAGE_VIEWED = "onboarding_video_page_viewed"
        const val SKIPPED = "onboarding_video_skipped"
        const val COMPLETED = "onboarding_video_completed"
        const val CLIP_FAILED = "onboarding_video_clip_failed"
    }

    object Key {
        const val PAGE = "page"
        const val PAGES = "pages"
    }
}
