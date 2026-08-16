package com.hopcape.odo.feature.dashboard

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.dashboard.presentation.home.HomeTelemetry

/**
 * The dashboard's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val dashboardAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        HomeTelemetry.Event.OPENED,
        mapOf(
            HomeTelemetry.Key.BAND to PropertyType.STRING,
            HomeTelemetry.Key.IS_NEW_USER to PropertyType.BOOLEAN,
            HomeTelemetry.Key.HAS_ATTENTION to PropertyType.BOOLEAN,
            HomeTelemetry.Key.SETUP_DONE to PropertyType.INT,
        ),
    ),
    EventSchema(HomeTelemetry.Event.BREAKDOWN_OPENED, emptyMap()),
    EventSchema(
        HomeTelemetry.Event.ATTENTION_TAPPED,
        mapOf(HomeTelemetry.Key.KIND to PropertyType.STRING),
    ),
    EventSchema(HomeTelemetry.Event.RECENT_OPENED, emptyMap()),
    EventSchema(HomeTelemetry.Event.TIMELINE_OPENED, emptyMap()),
    EventSchema(
        HomeTelemetry.Event.SCAN_BILL_TAPPED,
        mapOf(HomeTelemetry.Key.FROM_CHECKLIST to PropertyType.BOOLEAN),
    ),
    EventSchema(HomeTelemetry.Event.ADD_DOCUMENTS_TAPPED, emptyMap()),
    EventSchema(HomeTelemetry.Event.ADD_CAR_TAPPED, emptyMap()),
    EventSchema(HomeTelemetry.Event.AUTO_DETECT_PAYWALLED, emptyMap()),
    EventSchema(
        HomeTelemetry.Event.READ_FAILED,
        mapOf(HomeTelemetry.Key.REASON to PropertyType.STRING),
    ),
)
