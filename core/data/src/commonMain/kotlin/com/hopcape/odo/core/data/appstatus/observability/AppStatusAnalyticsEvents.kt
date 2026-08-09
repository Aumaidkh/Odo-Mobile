package com.hopcape.odo.core.data.appstatus.observability

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType

/**
 * The app-availability gate's analytics schema. Debug builds validate strictly and drop
 * anything not declared here, so an unregistered event is invisible in exactly the builds
 * where someone is watching.
 */
val appStatusAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        AppStatusTelemetry.EVENT_BLOCKED,
        mapOf("reason" to PropertyType.STRING),
    ),
    EventSchema(AppStatusTelemetry.EVENT_RELEASED),
)
