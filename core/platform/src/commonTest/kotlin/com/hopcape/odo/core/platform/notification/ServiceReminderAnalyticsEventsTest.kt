package com.hopcape.odo.core.platform.notification

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.api.problemsWith
import com.hopcape.odo.core.domain.servicelog.policy.ServiceReminder
import kotlin.test.Test
import kotlin.test.assertTrue

class ServiceReminderAnalyticsEventsTest {

    @Test
    fun everyServiceReminderEventIsDeclared() {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val analytics = object : AnalyticsTracker {
            override fun track(eventName: String, properties: Map<String, Any?>) {
                events += eventName to properties
            }
            override fun identify(traits: UserTraits) = Unit
            override fun setConsent(status: ConsentStatus) = Unit
            override fun flush() = Unit
        }
        ServiceReminderTelemetry(analytics).posted(ServiceReminder.Kind.DUE_SOON, "2027-03-01")

        val problems = events.flatMap { (name, props) -> serviceReminderAnalyticsEvents.problemsWith(name, props) }
        assertTrue(events.size == 1 && problems.isEmpty(), problems.joinToString("\n"))
    }
}
