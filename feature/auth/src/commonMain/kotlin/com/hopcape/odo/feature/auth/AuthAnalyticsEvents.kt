package com.hopcape.odo.feature.auth

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType

/**
 * Auth's analytics schema.
 *
 * Registering is not optional: debug builds validate strictly and drop anything not declared
 * here, so an unregistered event is invisible in exactly the builds where someone is watching.
 */
val authAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(AuthTelemetry.EVENT_SIGNED_IN, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_SIGNED_OUT, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_SESSION_ENDED, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_SKIPPED, mapOf(AuthTelemetry.Key.STEP to PropertyType.STRING)),
    EventSchema(AuthTelemetry.EVENT_ATTEMPTS_EXHAUSTED, emptyMap()),
)
