package com.hopcape.odo.feature.reminders

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.reminders.presentation.RemindersTelemetry

/**
 * The reminders feature's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so
 * an event the feature emits but this list omits is one nobody will ever see on a
 * dashboard. Only properties the telemetry facade always sends are listed.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val remindersAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        RemindersTelemetry.Event.OPENED,
        mapOf(
            RemindersTelemetry.Key.THIS_WEEK_COUNT to PropertyType.INT,
            RemindersTelemetry.Key.UPCOMING_COUNT to PropertyType.INT,
            RemindersTelemetry.Key.SUGGESTION_COUNT to PropertyType.INT,
        ),
    ),
    EventSchema(
        RemindersTelemetry.Event.READ_FAILED,
        mapOf(
            RemindersTelemetry.Key.SOURCE to PropertyType.STRING,
            RemindersTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(
        RemindersTelemetry.Event.DISMISSED,
        mapOf(RemindersTelemetry.Key.KIND to PropertyType.STRING),
    ),
    EventSchema(
        RemindersTelemetry.Event.DISMISS_FAILED,
        mapOf(
            RemindersTelemetry.Key.KIND to PropertyType.STRING,
            RemindersTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(
        RemindersTelemetry.Event.TURNED_OFF,
        mapOf(
            RemindersTelemetry.Key.KIND to PropertyType.STRING,
            RemindersTelemetry.Key.CUSTOM to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        RemindersTelemetry.Event.TURN_OFF_FAILED,
        mapOf(
            RemindersTelemetry.Key.KIND to PropertyType.STRING,
            RemindersTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(
        RemindersTelemetry.Event.CUSTOM_CREATED,
        mapOf(
            RemindersTelemetry.Key.CADENCE to PropertyType.STRING,
            RemindersTelemetry.Key.PRESET to PropertyType.STRING,
            RemindersTelemetry.Key.VIA_SUGGESTION to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        RemindersTelemetry.Event.CUSTOM_EDITED,
        mapOf(
            RemindersTelemetry.Key.CADENCE to PropertyType.STRING,
            RemindersTelemetry.Key.PRESET to PropertyType.STRING,
            RemindersTelemetry.Key.VIA_SUGGESTION to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(RemindersTelemetry.Event.CUSTOM_DELETED),
    EventSchema(
        RemindersTelemetry.Event.SAVE_FAILED,
        mapOf(
            RemindersTelemetry.Key.CADENCE to PropertyType.STRING,
            RemindersTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(
        RemindersTelemetry.Event.SETTINGS_CHANGED,
        mapOf(
            RemindersTelemetry.Key.FIELD to PropertyType.STRING,
            RemindersTelemetry.Key.ENABLED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        RemindersTelemetry.Event.SETTINGS_FAILED,
        mapOf(
            RemindersTelemetry.Key.FIELD to PropertyType.STRING,
            RemindersTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
)
