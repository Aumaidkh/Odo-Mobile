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
    EventSchema(
        SetupTelemetry.Event.MANUAL_ENTRY_CHOSEN,
        mapOf(SetupTelemetry.Key.HAD_MATCH to PropertyType.BOOLEAN),
    ),
    EventSchema(
        SetupTelemetry.Event.PLATE_LOOKUP,
        mapOf(SetupTelemetry.Key.OUTCOME to PropertyType.STRING),
    ),
    EventSchema(
        SetupTelemetry.Event.GOAL_SELECTED,
        mapOf(SetupTelemetry.Key.GOAL to PropertyType.STRING),
    ),
    // Make and fuel are nullable at the call site (the plate route may have neither), so they
    // are declared by absence rather than as required properties — a required property with a
    // null value is exactly what strict validation throws on.
    EventSchema(
        SetupTelemetry.Event.CAR_SAVED,
        mapOf(SetupTelemetry.Key.EDIT to PropertyType.BOOLEAN),
    ),
    EventSchema(SetupTelemetry.Event.PROFILE_SAVED),
    EventSchema(
        SetupTelemetry.Event.SAVE_FAILED,
        mapOf(
            SetupTelemetry.Key.STEP to PropertyType.STRING,
            SetupTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(SetupTelemetry.Event.FIRST_SCAN_CLICKED),
    EventSchema(SetupTelemetry.Event.FIRST_SCAN_SKIPPED),
    // `destination` was a property here until goal-based routing was deleted. It named one of
    // three surfaces that all resolved to the dashboard, so it was a constant.
    EventSchema(
        SetupTelemetry.Event.COMPLETED,
        mapOf(
            SetupTelemetry.Key.GOAL to PropertyType.STRING,
            SetupTelemetry.Key.SIGN_IN_OFFERED to PropertyType.BOOLEAN,
        ),
    ),
)
