package com.hopcape.odo.core.platform.notification

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType

/** Counts evening summaries posted and skipped. [day] keeps a repeat out of the dedup window. */
internal class DriveSummaryTelemetry(private val analytics: AnalyticsTracker) {

    fun posted(drives: Int, km: Int, day: String) {
        analytics.track(Event.POSTED, mapOf(Key.DRIVES to drives, Key.KM to km, Key.DAY to day))
    }

    fun skipped(reason: String, day: String) {
        analytics.track(Event.SKIPPED, mapOf(Key.REASON to reason, Key.DAY to day))
    }

    /** [parts] lists the figures shown, such as "km,per_km". */
    fun weeklyPosted(parts: String, day: String) {
        analytics.track(Event.WEEKLY_POSTED, mapOf(Key.PARTS to parts, Key.DAY to day))
    }

    fun weeklySkipped(reason: String, day: String) {
        analytics.track(Event.WEEKLY_SKIPPED, mapOf(Key.REASON to reason, Key.DAY to day))
    }

    object Event {
        const val POSTED = "drive_summary_posted"
        const val SKIPPED = "drive_summary_skipped"
        const val WEEKLY_POSTED = "weekly_summary_posted"
        const val WEEKLY_SKIPPED = "weekly_summary_skipped"
    }

    object Key {
        const val DRIVES = "drives"
        const val KM = "km"
        const val REASON = "reason"
        const val DAY = "day"
        const val PARTS = "parts"
    }
}

/** The drive summary events, declared so debug builds keep them. Public for `odoAnalyticsEvents`. */
val driveSummaryAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        DriveSummaryTelemetry.Event.POSTED,
        mapOf(
            DriveSummaryTelemetry.Key.DRIVES to PropertyType.INT,
            DriveSummaryTelemetry.Key.KM to PropertyType.INT,
            DriveSummaryTelemetry.Key.DAY to PropertyType.STRING,
        ),
    ),
    EventSchema(
        DriveSummaryTelemetry.Event.SKIPPED,
        mapOf(DriveSummaryTelemetry.Key.REASON to PropertyType.STRING, DriveSummaryTelemetry.Key.DAY to PropertyType.STRING),
    ),
    EventSchema(
        DriveSummaryTelemetry.Event.WEEKLY_POSTED,
        mapOf(DriveSummaryTelemetry.Key.PARTS to PropertyType.STRING, DriveSummaryTelemetry.Key.DAY to PropertyType.STRING),
    ),
    EventSchema(
        DriveSummaryTelemetry.Event.WEEKLY_SKIPPED,
        mapOf(DriveSummaryTelemetry.Key.REASON to PropertyType.STRING, DriveSummaryTelemetry.Key.DAY to PropertyType.STRING),
    ),
)
