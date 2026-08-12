package com.hopcape.odo.feature.timeline

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.timeline.presentation.TimelineTelemetry

/**
 * The timeline's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val timelineAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        TimelineTelemetry.Event.OPENED,
        mapOf(
            TimelineTelemetry.Key.EVENT_COUNT to PropertyType.INT,
            TimelineTelemetry.Key.HAS_SERVICES to PropertyType.BOOLEAN,
            TimelineTelemetry.Key.IS_NEW_USER to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(TimelineTelemetry.Event.SERVICE_OPENED, emptyMap()),
    EventSchema(TimelineTelemetry.Event.ADD_BILL_TAPPED, emptyMap()),
    EventSchema(TimelineTelemetry.Event.SCAN_FIRST_TAPPED, emptyMap()),
    EventSchema(TimelineTelemetry.Event.SHARE_TAPPED, emptyMap()),
    EventSchema(TimelineTelemetry.Event.FILTER_OPENED, emptyMap()),
    EventSchema(
        TimelineTelemetry.Event.FILTER_APPLIED,
        mapOf(
            TimelineTelemetry.Key.CATEGORIES to PropertyType.STRING,
            TimelineTelemetry.Key.ONLY_FLAGGED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        TimelineTelemetry.Event.READ_FAILED,
        mapOf(TimelineTelemetry.Key.REASON to PropertyType.STRING),
    ),
)
