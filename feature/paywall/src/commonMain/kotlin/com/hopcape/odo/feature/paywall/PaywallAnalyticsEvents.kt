package com.hopcape.odo.feature.paywall

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.paywall.presentation.PaywallTelemetry

/**
 * The paywall's analytics taxonomy, declared for the tracker.
 *
 * Debug builds run strict schema validation and drop undeclared events, so until this
 * existed **none of the paywall's events survived the builds where the funnel is checked** —
 * the one funnel in the app that money depends on, invisible in exactly the place anyone
 * would look at it. Adding the one-time offers is what turned that up.
 *
 * Only properties the facade always sends are marked required.
 */
val paywallAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        PaywallTelemetry.Event.SHOWN,
        mapOf(PaywallTelemetry.Key.TRIGGER to PropertyType.STRING),
    ),
    EventSchema(
        PaywallTelemetry.Event.OFFER_UNAVAILABLE,
        mapOf(
            PaywallTelemetry.Key.TRIGGER to PropertyType.STRING,
            PaywallTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(
        PaywallTelemetry.Event.PLAN_SELECTED,
        mapOf(
            PaywallTelemetry.Key.TRIGGER to PropertyType.STRING,
            PaywallTelemetry.Key.PLAN to PropertyType.STRING,
        ),
    ),
    EventSchema(
        PaywallTelemetry.Event.CHECKOUT_STARTED,
        mapOf(
            PaywallTelemetry.Key.TRIGGER to PropertyType.STRING,
            PaywallTelemetry.Key.PLAN to PropertyType.STRING,
            PaywallTelemetry.Key.TRIAL to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        PaywallTelemetry.Event.PURCHASE_COMPLETED,
        mapOf(
            PaywallTelemetry.Key.TRIGGER to PropertyType.STRING,
            PaywallTelemetry.Key.PLAN to PropertyType.STRING,
            PaywallTelemetry.Key.TRIAL to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        PaywallTelemetry.Event.PURCHASE_CANCELLED,
        mapOf(
            PaywallTelemetry.Key.TRIGGER to PropertyType.STRING,
            PaywallTelemetry.Key.PLAN to PropertyType.STRING,
        ),
    ),
    EventSchema(
        PaywallTelemetry.Event.PURCHASE_FAILED,
        mapOf(
            PaywallTelemetry.Key.TRIGGER to PropertyType.STRING,
            PaywallTelemetry.Key.PLAN to PropertyType.STRING,
        ),
    ),
    EventSchema(
        PaywallTelemetry.Event.RESTORE_TAPPED,
        mapOf(PaywallTelemetry.Key.TRIGGER to PropertyType.STRING),
    ),
    EventSchema(
        PaywallTelemetry.Event.RESTORE_FINISHED,
        mapOf(
            PaywallTelemetry.Key.TRIGGER to PropertyType.STRING,
            PaywallTelemetry.Key.RESTORED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        PaywallTelemetry.Event.DISMISSED,
        mapOf(PaywallTelemetry.Key.TRIGGER to PropertyType.STRING),
    ),
    // One-time offers. COUNT is the number that matters on the first: zero means the
    // products do not exist in the store yet and the owner was shown an empty sheet.
    EventSchema(
        PaywallTelemetry.Event.ONE_TIME_SHOWN,
        mapOf(PaywallTelemetry.Key.COUNT to PropertyType.INT),
    ),
    EventSchema(
        PaywallTelemetry.Event.ONE_TIME_UNAVAILABLE,
        mapOf(PaywallTelemetry.Key.REASON to PropertyType.STRING),
    ),
    EventSchema(
        PaywallTelemetry.Event.ONE_TIME_TAPPED,
        mapOf(PaywallTelemetry.Key.PRODUCT to PropertyType.STRING),
    ),
)
