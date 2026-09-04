package com.hopcape.odo.feature.billcheck

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.billcheck.presentation.BillCheckTelemetry

/**
 * The bill check's analytics taxonomy, declared for the tracker.
 *
 * Debug builds run strict schema validation and drop undeclared events, so a name missing
 * here is a name invisible in exactly the builds where it gets checked.
 */
val billCheckAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        BillCheckTelemetry.Event.RESULT_SHOWN,
        mapOf(
            BillCheckTelemetry.Key.FLAGGED to PropertyType.INT,
            BillCheckTelemetry.Key.LINES to PropertyType.INT,
            BillCheckTelemetry.Key.LOCKED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(BillCheckTelemetry.Event.SHARE_CLICKED),
    EventSchema(BillCheckTelemetry.Event.BASIS_OPENED),
    EventSchema(BillCheckTelemetry.Event.OFFERS_OPENED),
    EventSchema(BillCheckTelemetry.Event.ADD_LAST_BILL_CLICKED),
    EventSchema(BillCheckTelemetry.Event.WRONG_PRICE_REPORTED),
)
