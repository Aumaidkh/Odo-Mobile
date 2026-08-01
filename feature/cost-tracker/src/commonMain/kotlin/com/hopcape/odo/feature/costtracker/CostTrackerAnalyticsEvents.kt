package com.hopcape.odo.feature.costtracker

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.costtracker.presentation.CostTrackerTelemetry

/**
 * The cost tracker's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val costTrackerAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        CostTrackerTelemetry.Event.COST_OPENED,
        mapOf(
            CostTrackerTelemetry.Key.PERIOD to PropertyType.STRING,
            CostTrackerTelemetry.Key.HAS_RATE to PropertyType.BOOLEAN,
            CostTrackerTelemetry.Key.FUEL_ESTIMATED to PropertyType.BOOLEAN,
            CostTrackerTelemetry.Key.KM_DRIVEN to PropertyType.INT,
        ),
    ),
    EventSchema(
        CostTrackerTelemetry.Event.PERIOD_CHANGED,
        mapOf(CostTrackerTelemetry.Key.PERIOD to PropertyType.STRING),
    ),
    EventSchema(
        CostTrackerTelemetry.Event.READ_FAILED,
        mapOf(CostTrackerTelemetry.Key.REASON to PropertyType.STRING),
    ),
    EventSchema(
        CostTrackerTelemetry.Event.FUEL_RATE_SAVED,
        mapOf(CostTrackerTelemetry.Key.FUEL_TYPE to PropertyType.STRING),
    ),
    EventSchema(
        CostTrackerTelemetry.Event.FUEL_RATE_CLEARED,
        mapOf(CostTrackerTelemetry.Key.FUEL_TYPE to PropertyType.STRING),
    ),
    EventSchema(
        CostTrackerTelemetry.Event.FUEL_RATE_REFUSED,
        mapOf(CostTrackerTelemetry.Key.REASON to PropertyType.STRING),
    ),
)
