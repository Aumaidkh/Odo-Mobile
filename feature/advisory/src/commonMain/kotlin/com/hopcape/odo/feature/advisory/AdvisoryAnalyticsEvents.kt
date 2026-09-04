package com.hopcape.odo.feature.advisory

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.advisory.presentation.AdvisoryTelemetry

/**
 * The advisory feature's analytics taxonomy, declared for the tracker.
 *
 * Debug builds run strict schema validation and drop undeclared events, so a name missing
 * here is a name invisible in exactly the builds where it gets checked.
 */
val advisoryAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        AdvisoryTelemetry.Event.VALUE_SHOWN,
        mapOf(AdvisoryTelemetry.Key.HAS_RECORD to PropertyType.BOOLEAN),
    ),
    EventSchema(AdvisoryTelemetry.Event.SCAN_CLICKED),
    EventSchema(AdvisoryTelemetry.Event.SHARE_CLICKED),
)
