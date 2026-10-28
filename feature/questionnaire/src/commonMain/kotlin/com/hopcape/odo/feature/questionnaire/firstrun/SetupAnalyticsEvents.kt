package com.hopcape.odo.feature.questionnaire.firstrun

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.SetupTelemetry

private val step = mapOf(SetupTelemetry.Key.STEP to PropertyType.STRING)

/**
 * First-run setup's analytics taxonomy, declared for the tracker.
 *
 * Debug builds run strict schema validation and drop undeclared events, so a name missing
 * here is a name invisible in exactly the builds where it gets checked.
 *
 * Properties the facade always sends are required; the rest are optional.
 */
val setupAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(SetupTelemetry.Event.STARTED),
    EventSchema(SetupTelemetry.Event.STEP_ADVANCED, step),
    EventSchema(SetupTelemetry.Event.STEP_BACK, step),
    EventSchema(SetupTelemetry.Event.STEP_SHOWN, step),
    EventSchema(
        SetupTelemetry.Event.MODEL_CHOSEN,
        mapOf(SetupTelemetry.Key.SOURCE to PropertyType.STRING),
    ),
    EventSchema(SetupTelemetry.Event.MODEL_SEARCH_OPENED),
    EventSchema(SetupTelemetry.Event.MODEL_SEARCH_DISMISSED),
    EventSchema(SetupTelemetry.Event.CHANGE_MAKE_CLICKED),
    EventSchema(SetupTelemetry.Event.YEAR_OLDER_OPENED),
    EventSchema(
        SetupTelemetry.Event.YEAR_CHOSEN,
        mapOf(
            SetupTelemetry.Key.YEAR to PropertyType.INT,
            SetupTelemetry.Key.SOURCE to PropertyType.STRING,
            SetupTelemetry.Key.CHANGED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        SetupTelemetry.Event.FUEL_CHOSEN,
        mapOf(
            SetupTelemetry.Key.FUEL_TYPE to PropertyType.STRING,
            SetupTelemetry.Key.CHANGED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        SetupTelemetry.Event.ABANDONED,
        mapOf(
            SetupTelemetry.Key.STEP to PropertyType.STRING,
            SetupTelemetry.Key.FIELDS_ANSWERED to PropertyType.INT,
            SetupTelemetry.Key.STOPPED_AT to PropertyType.STRING,
            SetupTelemetry.Key.CATALOG to PropertyType.STRING,
        ),
    ),
    EventSchema(
        SetupTelemetry.Event.FIELD_ANSWERED,
        mapOf(
            SetupTelemetry.Key.STEP to PropertyType.STRING,
            SetupTelemetry.Key.FIELD to PropertyType.STRING,
        ),
    ),
    EventSchema(SetupTelemetry.Event.CATALOG_RETRIED),
    EventSchema(SetupTelemetry.Event.CATALOG_LOAD_FAILED),
    EventSchema(SetupTelemetry.Event.CATALOG_EMPTY),
    EventSchema(
        SetupTelemetry.Event.MODELS_EMPTY,
        mapOf(SetupTelemetry.Key.MAKE to PropertyType.STRING),
    ),
    EventSchema(
        SetupTelemetry.Event.GOAL_SELECTED,
        mapOf(SetupTelemetry.Key.GOAL to PropertyType.STRING),
    ),
    EventSchema(
        SetupTelemetry.Event.WORKSHOP_TIER_SELECTED,
        mapOf(SetupTelemetry.Key.WORKSHOP_TIER to PropertyType.STRING),
    ),
    EventSchema(
        SetupTelemetry.Event.LAST_SERVICE_FORGOTTEN,
        mapOf(SetupTelemetry.Key.FORGOT to PropertyType.BOOLEAN),
    ),
    EventSchema(SetupTelemetry.Event.LAST_SERVICE_SKIPPED),
    EventSchema(
        SetupTelemetry.Event.LAST_SERVICE_REFUSED,
        mapOf(SetupTelemetry.Key.FIELD to PropertyType.STRING),
    ),
    // Make and fuel are null when unknown, and a required property with a null value fails.
    EventSchema(
        SetupTelemetry.Event.CAR_SAVED,
        mapOf(
            SetupTelemetry.Key.EDIT to PropertyType.BOOLEAN,
            SetupTelemetry.Key.RESUMED to PropertyType.BOOLEAN,
        ),
        optionalProperties = mapOf(
            SetupTelemetry.Key.MAKE to PropertyType.STRING,
            SetupTelemetry.Key.FUEL_TYPE to PropertyType.STRING,
        ),
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
)
