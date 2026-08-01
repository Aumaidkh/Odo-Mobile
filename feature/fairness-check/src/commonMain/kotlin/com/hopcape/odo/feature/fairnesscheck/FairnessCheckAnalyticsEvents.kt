package com.hopcape.odo.feature.fairnesscheck

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessTelemetry

/**
 * The fairness check's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val fairnessCheckAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        FairnessTelemetry.Event.CHECKED,
        mapOf(
            FairnessTelemetry.Key.OUTCOME to PropertyType.STRING,
            FairnessTelemetry.Key.SAMPLE_SIZE to PropertyType.INT,
            FairnessTelemetry.Key.LINE_COUNT to PropertyType.INT,
            FairnessTelemetry.Key.BENCHMARKED_LINES to PropertyType.INT,
        ),
    ),
    EventSchema(FairnessTelemetry.Event.NO_CITY, emptyMap()),
    EventSchema(
        FairnessTelemetry.Event.FAILED,
        mapOf(FairnessTelemetry.Key.REASON to PropertyType.STRING),
    ),
    EventSchema(FairnessTelemetry.Event.REPORT_TAPPED, emptyMap()),
    EventSchema(FairnessTelemetry.Event.SET_CITY_TAPPED, emptyMap()),
)
