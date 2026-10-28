package com.hopcape.odo.core.platform.review

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.api.problemsWith
import kotlin.test.Test
import kotlin.test.assertTrue

class ReviewAnalyticsEventsTest {

    @Test
    fun everyReviewEventIsDeclared() {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val analytics = object : AnalyticsTracker {
            override fun track(eventName: String, properties: Map<String, Any?>) {
                events += eventName to properties
            }
            override fun identify(traits: UserTraits) = Unit
            override fun setConsent(status: ConsentStatus) = Unit
            override fun flush() = Unit
        }
        ReviewTelemetry(analytics).apply {
            requested(ReviewTrigger.FIRST_BILL)
            skipped(ReviewTrigger.TRIP_DAY_7, "cooldown")
        }

        val problems = events.flatMap { (name, props) -> reviewAnalyticsEvents.problemsWith(name, props) }
        assertTrue(events.size == 2 && problems.isEmpty(), problems.joinToString("\n"))
    }
}
