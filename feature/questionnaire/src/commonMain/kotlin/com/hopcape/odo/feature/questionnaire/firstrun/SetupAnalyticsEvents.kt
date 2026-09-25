package com.hopcape.odo.feature.questionnaire.firstrun

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.SetupTelemetry

/**
 * First-run setup's analytics taxonomy, declared for the tracker.
 *
 * Debug builds run strict schema validation and drop undeclared events, so a name missing
 * here is a name invisible in exactly the builds where it gets checked.
 *
 * Only properties the telemetry facade always sends are marked required.
 */
val setupAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(SetupTelemetry.Event.STARTED),
    EventSchema(
        SetupTelemetry.Event.STEP_ADVANCED,
        mapOf(SetupTelemetry.Key.STEP to PropertyType.STRING),
    ),
    EventSchema(
        SetupTelemetry.Event.ABANDONED,
        mapOf(SetupTelemetry.Key.STEP to PropertyType.STRING),
    ),
    // Make and fuel are nullable at the call site, so they are declared by absence rather
    // than as required properties — a required property with a null value is exactly what
    // strict validation throws on.
    EventSchema(
        SetupTelemetry.Event.CAR_SAVED,
        mapOf(SetupTelemetry.Key.EDIT to PropertyType.BOOLEAN),
    ),
    EventSchema(
        SetupTelemetry.Event.SAVE_FAILED,
        mapOf(
            SetupTelemetry.Key.STEP to PropertyType.STRING,
            SetupTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(SetupTelemetry.Event.ODOMETER_SKIPPED),
    EventSchema(SetupTelemetry.Event.COMPLETED),
    EventSchema(
        SetupTelemetry.Event.STAMP_FAILED,
        mapOf(SetupTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
)
