package com.hopcape.odo.feature.challan

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.challan.presentation.ChallanTelemetry

/**
 * The challans feature's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val challanAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        ChallanTelemetry.Event.LIST_OPENED,
        mapOf(
            ChallanTelemetry.Key.PENDING to PropertyType.INT,
            ChallanTelemetry.Key.COURT to PropertyType.INT,
        ),
    ),
    EventSchema(
        ChallanTelemetry.Event.REFRESHED,
        mapOf(ChallanTelemetry.Key.SUCCESS to PropertyType.BOOLEAN),
    ),
    EventSchema(
        ChallanTelemetry.Event.PAY_TAPPED,
        mapOf(ChallanTelemetry.Key.PENDING to PropertyType.INT),
    ),
    EventSchema(
        ChallanTelemetry.Event.MARKED_PAID,
        mapOf(ChallanTelemetry.Key.PENDING to PropertyType.INT),
    ),
    EventSchema(ChallanTelemetry.Event.LOOKUP_SUBMITTED),
    EventSchema(
        ChallanTelemetry.Event.LOOKUP_ANSWERED,
        mapOf(ChallanTelemetry.Key.OUTCOME to PropertyType.STRING),
    ),
)
