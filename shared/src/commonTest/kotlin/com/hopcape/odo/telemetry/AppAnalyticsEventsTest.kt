package com.hopcape.odo.telemetry

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.api.problemsWith
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.core.common.id.IdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppAnalyticsEventsTest {

    @Test
    fun everyEventTheAppSendsIsDeclared() {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val analytics = object : AnalyticsTracker {
            override fun track(eventName: String, properties: Map<String, Any?>) {
                events += eventName to properties
            }
            override fun identify(traits: UserTraits) = Unit
            override fun setConsent(status: ConsentStatus) = Unit
            override fun flush() = Unit
        }
        var next = 0
        val telemetry = AppTelemetry(analytics, HLogger.asLogger(), IdGenerator { "tap-${next++}" })
        telemetry.notificationOpened("engagement_nudge")
        telemetry.notificationOpened("engagement_nudge")

        val problems = events.flatMap { (name, props) -> appAnalyticsEvents.problemsWith(name, props) }
        assertTrue(events.isNotEmpty() && problems.isEmpty(), problems.joinToString("\n"))
        // The tracker drops exact repeats, so two taps of one kind must differ.
        assertEquals(2, events.map { it.second }.distinct().size)
    }
}
