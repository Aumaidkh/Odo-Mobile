package com.hopcape.odo.core.platform.notification

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.core.domain.servicelog.policy.ServiceReminder

/** Counts service-due notifications posted, so opens can be read against them. */
internal class ServiceReminderTelemetry(private val analytics: AnalyticsTracker) {

    /** [dueOn] varies per service, which keeps a repeat out of the tracker's dedup window. */
    fun posted(kind: ServiceReminder.Kind, dueOn: String) {
        analytics.track(Event.POSTED, mapOf(Key.KIND to kind.name.lowercase(), Key.DUE_ON to dueOn))
    }

    object Event {
        const val POSTED = "service_reminder_posted"
    }

    object Key {
        const val KIND = "kind"
        const val DUE_ON = "due_on"
    }
}

/** The service reminder events, declared so debug builds keep them. Public for `odoAnalyticsEvents`. */
val serviceReminderAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        ServiceReminderTelemetry.Event.POSTED,
        mapOf(ServiceReminderTelemetry.Key.KIND to PropertyType.STRING, ServiceReminderTelemetry.Key.DUE_ON to PropertyType.STRING),
    ),
)
