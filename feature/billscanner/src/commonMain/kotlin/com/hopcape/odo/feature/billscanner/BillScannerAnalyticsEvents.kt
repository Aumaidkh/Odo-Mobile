package com.hopcape.odo.feature.billscanner

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTelemetry

/**
 * The bill scanner's analytics taxonomy, declared for the tracker.
 *
 * Debug builds validate against this list and **drop** events that are not declared, so an
 * event the feature emits but this list omits is one nobody will ever see on a dashboard.
 * That matters more here than anywhere else in the app: bills scanned per month is the PRD's
 * North Star metric, and these events are how it is counted.
 *
 * Only properties the telemetry facade always sends are listed. It is the sole caller of every
 * name below, which is what makes that safe to promise.
 *
 * Public because the app bootstrap assembles the config (`odoAnalyticsEvents`).
 */
val billScannerAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        BillScannerTelemetry.Event.CAMERA_PERMISSION_ANSWERED,
        mapOf(BillScannerTelemetry.Key.STATUS to PropertyType.STRING),
    ),
    EventSchema(BillScannerTelemetry.Event.CAMERA_DECLINED),
    EventSchema(
        BillScannerTelemetry.Event.READ_FAILED,
        mapOf(
            BillScannerTelemetry.Key.SOURCE to PropertyType.STRING,
            BillScannerTelemetry.Key.REASON to PropertyType.STRING,
        ),
    ),
    EventSchema(
        BillScannerTelemetry.Event.SCANNER_OPENED,
        mapOf(BillScannerTelemetry.Key.TARGET to PropertyType.STRING),
    ),
    EventSchema(
        BillScannerTelemetry.Event.TARGET_SWITCHED,
        mapOf(BillScannerTelemetry.Key.TARGET to PropertyType.STRING),
    ),
    EventSchema(
        BillScannerTelemetry.Event.PHOTO_CAPTURED,
        mapOf(BillScannerTelemetry.Key.TARGET to PropertyType.STRING),
    ),
    EventSchema(
        BillScannerTelemetry.Event.CAMERA_FAILED,
        mapOf(BillScannerTelemetry.Key.REASON to PropertyType.STRING),
    ),
    EventSchema(
        BillScannerTelemetry.Event.BILL_EXTRACTED,
        mapOf(
            BillScannerTelemetry.Key.CONFIDENCE to PropertyType.INT,
            BillScannerTelemetry.Key.BILL_TYPE to PropertyType.STRING,
            BillScannerTelemetry.Key.LINE_ITEM_COUNT to PropertyType.INT,
            BillScannerTelemetry.Key.MANUAL_REVIEW to PropertyType.BOOLEAN,
            BillScannerTelemetry.Key.HAS_ODOMETER to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        BillScannerTelemetry.Event.DOCUMENT_EXTRACTED,
        mapOf(BillScannerTelemetry.Key.HAS_EXPIRY to PropertyType.BOOLEAN),
    ),
    EventSchema(
        BillScannerTelemetry.Event.PUMP_EXTRACTED,
        mapOf(
            BillScannerTelemetry.Key.FIELDS_READ to PropertyType.INT,
            BillScannerTelemetry.Key.CROSS_CHECKED to PropertyType.BOOLEAN,
        ),
    ),
    EventSchema(
        BillScannerTelemetry.Event.EXTRACTION_FAILED,
        mapOf(BillScannerTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
    EventSchema(
        BillScannerTelemetry.Event.BILL_SAVED,
        mapOf(BillScannerTelemetry.Key.EDITED to PropertyType.BOOLEAN),
    ),
    EventSchema(
        BillScannerTelemetry.Event.DOCUMENT_SAVED,
        mapOf(
            BillScannerTelemetry.Key.TYPE to PropertyType.STRING,
            BillScannerTelemetry.Key.ORIGIN to PropertyType.STRING,
        ),
    ),
    EventSchema(
        BillScannerTelemetry.Event.SAVE_FAILED,
        mapOf(BillScannerTelemetry.Key.ERRORS to PropertyType.STRING),
    ),
)
