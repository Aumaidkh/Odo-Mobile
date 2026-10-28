package com.hopcape.odo.core.platform.notification

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.logging.api.Logger

/** Counts nudges posted and skipped, so opens can be read against what was sent. */
internal class NudgeTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
) {
    fun posted(nudge: EngagementNudge) {
        analytics.track(Event.POSTED, mapOf(Key.NUDGE to nudge.name.lowercase()))
    }

    fun skipped(nudge: EngagementNudge, reason: BillNudgeCheck) {
        val fields = mapOf(Key.NUDGE to nudge.name.lowercase(), Key.REASON to reason.name.lowercase())
        analytics.track(Event.SKIPPED, fields)
        logger.info(TAG, Event.SKIPPED, fields = fields)
    }

    object Event {
        const val POSTED = "nudge_posted"
        const val SKIPPED = "nudge_skipped"
    }

    object Key {
        const val NUDGE = "nudge"
        const val REASON = "reason"
    }

    private companion object {
        const val TAG = "NUDGES"
    }
}

/** The nudge events, declared so debug builds keep them. Public for `odoAnalyticsEvents`. */
val nudgeAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(NudgeTelemetry.Event.POSTED, mapOf(NudgeTelemetry.Key.NUDGE to PropertyType.STRING)),
    EventSchema(
        NudgeTelemetry.Event.SKIPPED,
        mapOf(NudgeTelemetry.Key.NUDGE to PropertyType.STRING, NudgeTelemetry.Key.REASON to PropertyType.STRING),
    ),
)
