package com.hopcape.odo.core.platform.review

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType

/** Counts review requests. Play never says whether its sheet appeared, so "requested" is the most we know. */
internal class ReviewTelemetry(private val analytics: AnalyticsTracker) {

    fun requested(trigger: ReviewTrigger) {
        analytics.track(Event.REQUESTED, mapOf(Key.TRIGGER to trigger.value))
    }

    fun skipped(trigger: ReviewTrigger, reason: String) {
        analytics.track(Event.SKIPPED, mapOf(Key.TRIGGER to trigger.value, Key.REASON to reason))
    }

    object Event {
        const val REQUESTED = "review_prompt_requested"
        const val SKIPPED = "review_prompt_skipped"
    }

    object Key {
        const val TRIGGER = "trigger"
        const val REASON = "reason"
    }
}

/** The review events, declared so debug builds keep them. Public for `odoAnalyticsEvents`. */
val reviewAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(ReviewTelemetry.Event.REQUESTED, mapOf(ReviewTelemetry.Key.TRIGGER to PropertyType.STRING)),
    EventSchema(
        ReviewTelemetry.Event.SKIPPED,
        mapOf(ReviewTelemetry.Key.TRIGGER to PropertyType.STRING, ReviewTelemetry.Key.REASON to PropertyType.STRING),
    ),
)
