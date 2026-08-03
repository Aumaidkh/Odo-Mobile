package com.hopcape.odo.core.sync.observability

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType

/**
 * The sync engine's analytics schema.
 *
 * Registering is not optional. Debug builds validate strictly and **drop** anything not
 * declared here, so an unregistered event is invisible in exactly the builds where someone
 * is watching — which is how `sync_failed` came to be logged and never recorded.
 */
val syncAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        SyncTelemetry.EVENT_RUN_COMPLETED,
        mapOf(
            SyncTelemetry.Key.ENTITIES to PropertyType.INT,
            SyncTelemetry.Key.DURATION_MS to PropertyType.LONG,
        ),
    ),
    EventSchema(
        SyncTelemetry.EVENT_RUN_FAILED,
        mapOf(
            SyncTelemetry.Key.ENTITY to PropertyType.STRING,
            SyncTelemetry.Key.ERROR to PropertyType.STRING,
        ),
    ),
    EventSchema(
        SyncTelemetry.EVENT_CONFLICT_RESOLVED,
        mapOf(
            SyncTelemetry.Key.ENTITY to PropertyType.STRING,
            SyncTelemetry.Key.WINNER to PropertyType.STRING,
        ),
    ),
)
