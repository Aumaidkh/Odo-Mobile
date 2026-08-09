package com.hopcape.odo.feature.documentvault

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry

/**
 * The document vault's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 *
 * Only properties the telemetry facade always sends are listed. It is the sole caller of
 * every name below, which is what makes that safe to promise.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val documentVaultAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        DocumentVaultTelemetry.Event.VAULT_OPENED,
        mapOf(
            DocumentVaultTelemetry.Key.ON_FILE_COUNT to PropertyType.INT,
            DocumentVaultTelemetry.Key.MISSING_COUNT to PropertyType.INT,
            DocumentVaultTelemetry.Key.ATTENTION_COUNT to PropertyType.INT,
        ),
    ),
    EventSchema(
        DocumentVaultTelemetry.Event.READ_FAILED,
        mapOf(
            DocumentVaultTelemetry.Key.SOURCE to PropertyType.STRING,
            DocumentVaultTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(
        DocumentVaultTelemetry.Event.DOCUMENT_OPENED,
        mapOf(
            DocumentVaultTelemetry.Key.TYPE to PropertyType.STRING,
            DocumentVaultTelemetry.Key.VERIFIED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        DocumentVaultTelemetry.Event.DOCUMENT_PREVIEWED,
        mapOf(DocumentVaultTelemetry.Key.TYPE to PropertyType.STRING),
    ),

    EventSchema(
        DocumentVaultTelemetry.Event.ADD_OPENED,
        mapOf(DocumentVaultTelemetry.Key.PREFILLED to PropertyType.BOOLEAN),
    ),
    EventSchema(
        DocumentVaultTelemetry.Event.TYPE_SELECTED,
        mapOf(DocumentVaultTelemetry.Key.TYPE to PropertyType.STRING),
    ),
    EventSchema(
        DocumentVaultTelemetry.Event.CAPTURE_UNAVAILABLE,
        mapOf(DocumentVaultTelemetry.Key.METHOD to PropertyType.STRING),
    ),

    EventSchema(
        DocumentVaultTelemetry.Event.DOCUMENT_ADDED,
        mapOf(
            DocumentVaultTelemetry.Key.TYPE to PropertyType.STRING,
            DocumentVaultTelemetry.Key.SOURCE to PropertyType.STRING,
            DocumentVaultTelemetry.Key.HAS_EXPIRY to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        DocumentVaultTelemetry.Event.SAVE_FAILED,
        mapOf(
            DocumentVaultTelemetry.Key.TYPE to PropertyType.STRING,
            DocumentVaultTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(
        DocumentVaultTelemetry.Event.LIMIT_REACHED,
        mapOf(
            DocumentVaultTelemetry.Key.TYPE to PropertyType.STRING,
            DocumentVaultTelemetry.Key.LIMIT to PropertyType.INT,
        ),
    ),

    EventSchema(
        DocumentVaultTelemetry.Event.FILE_REPLACED,
        mapOf(
            DocumentVaultTelemetry.Key.DOCUMENT_ID to PropertyType.STRING,
            DocumentVaultTelemetry.Key.SOURCE to PropertyType.STRING,
        ),
    ),
    EventSchema(
        DocumentVaultTelemetry.Event.REPLACE_FAILED,
        mapOf(
            DocumentVaultTelemetry.Key.DOCUMENT_ID to PropertyType.STRING,
            DocumentVaultTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),
    EventSchema(DocumentVaultTelemetry.Event.DOCUMENT_DELETED),
    EventSchema(
        DocumentVaultTelemetry.Event.DELETE_FAILED,
        mapOf(
            DocumentVaultTelemetry.Key.DOCUMENT_ID to PropertyType.STRING,
            DocumentVaultTelemetry.Key.ERRORS to PropertyType.STRING,
        ),
    ),

    EventSchema(DocumentVaultTelemetry.Event.SHARE_OPENED),
    EventSchema(
        DocumentVaultTelemetry.Event.DOCUMENT_SHARED,
        mapOf(
            DocumentVaultTelemetry.Key.TYPE to PropertyType.STRING,
            DocumentVaultTelemetry.Key.TARGET to PropertyType.STRING,
        ),
    ),
)
