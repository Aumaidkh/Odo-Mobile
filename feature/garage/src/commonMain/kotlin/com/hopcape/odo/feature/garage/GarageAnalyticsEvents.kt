package com.hopcape.odo.feature.garage

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.garage.presentation.GarageTelemetry

/**
 * The garage's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Only properties the telemetry facade always sends are listed. It is the sole caller of
 * every name below, which is what makes that safe to promise.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val garageAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        GarageTelemetry.Event.GARAGE_OPENED,
        mapOf(
            GarageTelemetry.Key.SERVICE_COUNT to PropertyType.INT,
            GarageTelemetry.Key.VERIFIED_COUNT to PropertyType.INT,
            GarageTelemetry.Key.MISSING_COUNT to PropertyType.INT,
            GarageTelemetry.Key.ATTENTION_COUNT to PropertyType.INT,
        ),
    ),
    EventSchema(GarageTelemetry.Event.GARAGE_EMPTY),
    EventSchema(
        GarageTelemetry.Event.READ_FAILED,
        mapOf(
            GarageTelemetry.Key.SOURCE to PropertyType.STRING,
            GarageTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(
        GarageTelemetry.Event.HISTORY_FILTERED,
        mapOf(GarageTelemetry.Key.FACET to PropertyType.STRING),
    ),
    EventSchema(
        GarageTelemetry.Event.NO_ACTIVE_CAR,
        mapOf(GarageTelemetry.Key.SOURCE to PropertyType.STRING),
    ),
    EventSchema(GarageTelemetry.Event.ODOMETER_OPENED),
    EventSchema(GarageTelemetry.Event.ODOMETER_UPDATED),
    EventSchema(
        GarageTelemetry.Event.ODOMETER_REFUSED,
        mapOf(GarageTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
    EventSchema(
        GarageTelemetry.Event.ODOMETER_FAILED,
        mapOf(GarageTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
    EventSchema(
        GarageTelemetry.Event.CAR_FORM_OPENED,
        mapOf(GarageTelemetry.Key.SOURCE to PropertyType.STRING),
    ),
    EventSchema(
        GarageTelemetry.Event.PLATE_LOOKED_UP,
        mapOf(
            GarageTelemetry.Key.FOUND to PropertyType.BOOLEAN,
            GarageTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(
        GarageTelemetry.Event.CAR_SAVED,
        mapOf(
            GarageTelemetry.Key.SOURCE to PropertyType.STRING,
            GarageTelemetry.Key.HAS_PLATE to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        GarageTelemetry.Event.CAR_SAVE_FAILED,
        mapOf(
            GarageTelemetry.Key.SOURCE to PropertyType.STRING,
            GarageTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(
        GarageTelemetry.Event.CAR_REMOVED,
        mapOf(
            GarageTelemetry.Key.SERVICE_COUNT to PropertyType.INT,
            GarageTelemetry.Key.DOCUMENT_COUNT to PropertyType.INT,
        ),
    ),
    EventSchema(
        GarageTelemetry.Event.CAR_REMOVE_FAILED,
        mapOf(GarageTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
    EventSchema(
        GarageTelemetry.Event.EXPORT_REQUESTED,
        mapOf(GarageTelemetry.Key.TARGET to PropertyType.STRING),
    ),
)
