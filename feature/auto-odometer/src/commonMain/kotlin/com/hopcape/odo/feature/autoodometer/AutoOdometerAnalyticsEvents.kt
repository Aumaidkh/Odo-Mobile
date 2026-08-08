package com.hopcape.odo.feature.autoodometer

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.autoodometer.presentation.AutoOdometerTelemetry

/**
 * The auto-odometer feature's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so
 * an event the feature emits but this list omits is one nobody will ever see on a
 * dashboard. Only properties the telemetry facade always sends are listed.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val autoOdometerAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(AutoOdometerTelemetry.Event.CARD_SHOWN),
    EventSchema(AutoOdometerTelemetry.Event.CARD_CTA),
    EventSchema(
        AutoOdometerTelemetry.Event.EDUCATION_VIEWED,
        mapOf(AutoOdometerTelemetry.Key.MODE to PropertyType.STRING),
    ),
    EventSchema(AutoOdometerTelemetry.Event.NO_BT_PATH_TAKEN),
    EventSchema(
        AutoOdometerTelemetry.Event.DEVICE_SELECTED,
        mapOf(
            AutoOdometerTelemetry.Key.CATEGORY to PropertyType.STRING,
            AutoOdometerTelemetry.Key.WAS_CONNECTED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        AutoOdometerTelemetry.Event.PERMISSION_ANSWERED,
        mapOf(
            AutoOdometerTelemetry.Key.STEP to PropertyType.STRING,
            AutoOdometerTelemetry.Key.STATUS to PropertyType.STRING,
        ),
    ),
    EventSchema(
        AutoOdometerTelemetry.Event.SETUP_COMPLETED,
        mapOf(AutoOdometerTelemetry.Key.MODE to PropertyType.STRING),
    ),
    EventSchema(AutoOdometerTelemetry.Event.FIRST_TRIP_LOGGED),
    EventSchema(AutoOdometerTelemetry.Event.TRIP_LOGGED_VIEWED),
    EventSchema(AutoOdometerTelemetry.Event.TRIP_REJECTED),
    EventSchema(
        AutoOdometerTelemetry.Event.BACKGROUND_UPGRADE_ANSWERED,
        mapOf(AutoOdometerTelemetry.Key.GRANTED to PropertyType.BOOLEAN),
    ),
    EventSchema(
        AutoOdometerTelemetry.Event.TRACKING_TOGGLED,
        mapOf(AutoOdometerTelemetry.Key.ON to PropertyType.BOOLEAN),
    ),
    EventSchema(AutoOdometerTelemetry.Event.PAUSED_WEEK),
    EventSchema(AutoOdometerTelemetry.Event.TRIP_DATA_DELETED),
    EventSchema(
        AutoOdometerTelemetry.Event.PRECONDITION_LOST,
        mapOf(AutoOdometerTelemetry.Key.WHICH to PropertyType.STRING),
    ),
    EventSchema(AutoOdometerTelemetry.Event.NO_ACTIVE_CAR),
)
