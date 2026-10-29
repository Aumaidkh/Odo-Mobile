package com.hopcape.odo.core.platform.notification

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.api.problemsWith
import kotlin.test.Test
import kotlin.test.assertTrue

class DriveSummaryAnalyticsEventsTest {

    @Test
    fun everyDriveSummaryEventIsDeclared() {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val analytics = object : AnalyticsTracker {
            override fun track(eventName: String, properties: Map<String, Any?>) {
                events += eventName to properties
            }
            override fun identify(traits: UserTraits) = Unit
            override fun setConsent(status: ConsentStatus) = Unit
            override fun flush() = Unit
        }
        DriveSummaryTelemetry(analytics).apply {
            posted(drives = 2, km = 18, day = "2026-10-28")
            skipped(reason = "no_drives", day = "2026-10-28")
            weeklyPosted(parts = "km,per_km", day = "2026-11-01")
            weeklySkipped(reason = "no_data", day = "2026-11-01")
        }

        val problems = events.flatMap { (name, props) -> driveSummaryAnalyticsEvents.problemsWith(name, props) }
        assertTrue(events.size == 4 && problems.isEmpty(), problems.joinToString("\n"))
    }
}
