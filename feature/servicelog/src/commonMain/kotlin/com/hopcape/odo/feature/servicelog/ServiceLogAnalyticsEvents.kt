package com.hopcape.odo.feature.servicelog

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTelemetry

/**
 * The service log's analytics taxonomy, declared for the tracker.
 *
 * Registration is not optional bookkeeping: debug builds run strict schema validation and
 * **drop** any event that isn't declared here, so an event the feature emits but this list
 * omits is one nobody will ever see on a dashboard.
 *
 * Declaring the properties as well as the names is the point of the schema — it is what
 * turns "the dashboard broke because someone typo'd a property" into a failure at the call
 * site. Only properties the telemetry facade *always* sends are marked required; the facade
 * is the sole caller of every name below, which is what makes that safe to promise.
 *
 * Public because the app bootstrap assembles the config (see `odoAnalyticsEvents`); it is
 * the one thing in this module besides [serviceLogModule] that another module references.
 */
val serviceLogAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(ServiceLogTelemetry.Event.LIST_OPENED),
    EventSchema(
        ServiceLogTelemetry.Event.READ_FAILED,
        mapOf(
            ServiceLogTelemetry.Key.SOURCE to PropertyType.STRING,
            ServiceLogTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(
        ServiceLogTelemetry.Event.DIRECTION_SELECTED,
        mapOf(ServiceLogTelemetry.Key.DIRECTION to PropertyType.STRING),
    ),
    EventSchema(
        ServiceLogTelemetry.Event.FILTER_SELECTED,
        mapOf(ServiceLogTelemetry.Key.FILTER to PropertyType.STRING),
    ),
    EventSchema(
        ServiceLogTelemetry.Event.SCAN_CLICKED,
        mapOf(ServiceLogTelemetry.Key.SOURCE to PropertyType.STRING),
    ),

    EventSchema(
        ServiceLogTelemetry.Event.ENTRY_OPENED,
        mapOf(
            ServiceLogTelemetry.Key.VERIFIED to PropertyType.BOOLEAN,
            ServiceLogTelemetry.Key.FLAGGED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        ServiceLogTelemetry.Event.ENTRY_SAVED,
        mapOf(
            ServiceLogTelemetry.Key.EDIT to PropertyType.BOOLEAN,
            ServiceLogTelemetry.Key.VERIFIED to PropertyType.BOOLEAN,
            ServiceLogTelemetry.Key.CATEGORIES to PropertyType.STRING,
        ),
    ),
    EventSchema(
        ServiceLogTelemetry.Event.SAVE_FAILED,
        mapOf(
            ServiceLogTelemetry.Key.EDIT to PropertyType.BOOLEAN,
            ServiceLogTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(ServiceLogTelemetry.Event.ENTRY_DELETED),
    EventSchema(
        ServiceLogTelemetry.Event.DELETE_FAILED,
        mapOf(
            ServiceLogTelemetry.Key.LOG_ID to PropertyType.STRING,
            ServiceLogTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),

    EventSchema(
        ServiceLogTelemetry.Event.REPORT_SUBMITTED,
        mapOf(ServiceLogTelemetry.Key.REASON to PropertyType.STRING),
    ),
    EventSchema(
        ServiceLogTelemetry.Event.REPORT_FAILED,
        mapOf(
            ServiceLogTelemetry.Key.REASON to PropertyType.STRING,
            ServiceLogTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),

    EventSchema(ServiceLogTelemetry.Event.SHARE_OPENED),
    EventSchema(
        ServiceLogTelemetry.Event.RECORD_SHARED,
        mapOf(ServiceLogTelemetry.Key.TARGET to PropertyType.STRING),
    ),
)
