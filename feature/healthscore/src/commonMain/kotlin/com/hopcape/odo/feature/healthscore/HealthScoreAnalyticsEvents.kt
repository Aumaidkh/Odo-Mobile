package com.hopcape.odo.feature.healthscore

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.healthscore.presentation.HealthScoreTelemetry

/**
 * The health score's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val healthScoreAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        HealthScoreTelemetry.Event.SCORE_OPENED,
        mapOf(
            HealthScoreTelemetry.Key.SCORE to PropertyType.INT,
            HealthScoreTelemetry.Key.BAND to PropertyType.STRING,
            HealthScoreTelemetry.Key.IS_PRO to PropertyType.BOOLEAN,
            HealthScoreTelemetry.Key.NOTHING_LOGGED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        HealthScoreTelemetry.Event.INFO_OPENED,
        mapOf(HealthScoreTelemetry.Key.SCORE to PropertyType.INT),
    ),
    EventSchema(
        HealthScoreTelemetry.Event.UNLOCK_TAPPED,
        mapOf(HealthScoreTelemetry.Key.SCORE to PropertyType.INT),
    ),
    EventSchema(
        HealthScoreTelemetry.Event.READ_FAILED,
        mapOf(HealthScoreTelemetry.Key.REASON to PropertyType.STRING),
    ),
)
