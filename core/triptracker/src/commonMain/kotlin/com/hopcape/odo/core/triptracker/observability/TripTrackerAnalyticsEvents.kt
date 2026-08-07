package com.hopcape.odo.core.triptracker.observability

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType

/**
 * This module's analytics taxonomy, contributed to `shared`'s `odoAnalyticsEvents`
 * (`shared/.../di/OdoAnalyticsEvents.kt`) the same way every feature does. Debug builds
 * drop unregistered events, so an event missing here is invisible in exactly the build
 * where the funnel is being checked.
 */
val tripTrackerAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(TripTrackerTelemetry.EVENT_TRACKING_ENABLED, emptyMap()),
    EventSchema(TripTrackerTelemetry.EVENT_TRACKING_DISABLED, emptyMap()),
    EventSchema(
        TripTrackerTelemetry.EVENT_TRIP_STARTED,
        mapOf(TripTrackerTelemetry.Key.MODE to PropertyType.STRING),
    ),
    EventSchema(
        TripTrackerTelemetry.EVENT_TRIP_SAVED,
        mapOf(
            TripTrackerTelemetry.Key.MODE to PropertyType.STRING,
            TripTrackerTelemetry.Key.DISTANCE_KM_BUCKET to PropertyType.STRING,
            TripTrackerTelemetry.Key.ESTIMATED_PCT_BUCKET to PropertyType.STRING,
        ),
    ),
    EventSchema(
        TripTrackerTelemetry.EVENT_TRIP_NEEDS_CONFIRMATION,
        mapOf(
            TripTrackerTelemetry.Key.MODE to PropertyType.STRING,
            TripTrackerTelemetry.Key.DISTANCE_KM_BUCKET to PropertyType.STRING,
            TripTrackerTelemetry.Key.ESTIMATED_PCT_BUCKET to PropertyType.STRING,
        ),
    ),
    EventSchema(
        TripTrackerTelemetry.EVENT_TRIP_DISCARDED,
        mapOf(
            TripTrackerTelemetry.Key.MODE to PropertyType.STRING,
            TripTrackerTelemetry.Key.DISTANCE_M to PropertyType.LONG,
            TripTrackerTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(TripTrackerTelemetry.EVENT_TRIP_GAP_INFERRED, emptyMap()),
    EventSchema(TripTrackerTelemetry.EVENT_TRIP_STITCH_RESUMED, emptyMap()),
    EventSchema(
        TripTrackerTelemetry.EVENT_PRECONDITION_LOST,
        mapOf(TripTrackerTelemetry.Key.WHICH to PropertyType.STRING),
    ),
    EventSchema(
        TripTrackerTelemetry.EVENT_SESSION_RESTORED,
        mapOf(TripTrackerTelemetry.Key.OUTCOME to PropertyType.STRING),
    ),
)
