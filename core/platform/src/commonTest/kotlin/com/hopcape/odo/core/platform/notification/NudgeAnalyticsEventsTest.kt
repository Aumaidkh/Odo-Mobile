package com.hopcape.odo.core.platform.notification

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.api.problemsWith
import com.hopcape.logging.api.HLogger
import kotlin.test.Test
import kotlin.test.assertTrue

class NudgeAnalyticsEventsTest {

    @Test
    fun everyNudgeEventIsDeclared() {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val analytics = object : AnalyticsTracker {
            override fun track(eventName: String, properties: Map<String, Any?>) {
                events += eventName to properties
            }
            override fun identify(traits: UserTraits) = Unit
            override fun setConsent(status: ConsentStatus) = Unit
            override fun flush() = Unit
        }
        NudgeTelemetry(HLogger.asLogger(), analytics).apply {
            posted(EngagementNudge.FIRST_SCORE)
            skipped(EngagementNudge.FIRST_SCORE, BillNudgeCheck.HAS_BILL)
        }

        val problems = events.flatMap { (name, props) -> nudgeAnalyticsEvents.problemsWith(name, props) }
        assertTrue(events.size == 2 && problems.isEmpty(), problems.joinToString("\n"))
    }
}
