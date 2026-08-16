package com.hopcape.odo.feature.refuel

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.refuel.presentation.RefuelTelemetry

/**
 * Refuel's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val refuelAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        RefuelTelemetry.Event.CONFIRM_OPENED,
        mapOf(
            RefuelTelemetry.Key.SOURCE to PropertyType.STRING,
            RefuelTelemetry.Key.PREFILLED_FIELDS to PropertyType.INT,
            RefuelTelemetry.Key.ODOMETER_PREDICTED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        RefuelTelemetry.Event.FILL_LOGGED,
        mapOf(
            RefuelTelemetry.Key.SOURCE to PropertyType.STRING,
            RefuelTelemetry.Key.CORRECTED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        RefuelTelemetry.Event.FILL_REFUSED,
        mapOf(
            RefuelTelemetry.Key.SOURCE to PropertyType.STRING,
            RefuelTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(
        RefuelTelemetry.Event.CAPTURE_REJECTED,
        mapOf(RefuelTelemetry.Key.SOURCE to PropertyType.STRING),
    ),
    EventSchema(
        RefuelTelemetry.Event.TANK_INSIGHT_SHOWN,
        mapOf(RefuelTelemetry.Key.COMPARISON to PropertyType.STRING),
    ),
    EventSchema(
        RefuelTelemetry.Event.DETECTION_TOGGLED,
        mapOf(RefuelTelemetry.Key.ENABLED to PropertyType.BOOLEAN),
    ),
    EventSchema(
        RefuelTelemetry.Event.SETUP_STEP_TAKEN,
        mapOf(RefuelTelemetry.Key.STEP to PropertyType.STRING),
    ),
)
