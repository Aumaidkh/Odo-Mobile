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
    EventSchema(AuthTelemetry.EVENT_OTP_REQUESTED, emptyMap()),
    // The reason is a class name and can be null for an anonymous type, so it is not required.
    EventSchema(AuthTelemetry.EVENT_OTP_REJECTED, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_PHONE_SHOWN, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_PHONE_TYPING_STARTED, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_PHONE_ENTERED, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_SEND_CODE_CLICKED, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_PHONE_REFUSED, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_OTP_SHOWN, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_OTP_TYPING_STARTED, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_OTP_ENTERED, mapOf(AuthTelemetry.Key.AUTO_READ to PropertyType.BOOLEAN)),
    EventSchema(AuthTelemetry.EVENT_OTP_RESEND_CLICKED, emptyMap()),
    EventSchema(AuthTelemetry.EVENT_CHANGE_NUMBER_CLICKED, emptyMap()),
)
