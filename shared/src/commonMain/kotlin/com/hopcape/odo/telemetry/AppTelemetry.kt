package com.hopcape.odo.telemetry

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.common.id.IdGenerator
import org.koin.dsl.module

/** Observability for the app shell itself, outside any feature. */
class AppTelemetry(
    private val analytics: AnalyticsTracker,
    private val logger: Logger,
    private val ids: IdGenerator,
) {

    /**
     * The app was opened by tapping a notification. [kind] is a `NotificationOpen` value.
     * [Key.TAP_ID] keeps two taps of one kind from matching the tracker's dedup window.
     */
    fun notificationOpened(kind: String) {
        val fields = mapOf(Key.KIND to kind, Key.TAP_ID to ids.newId())
        analytics.track(Event.NOTIFICATION_OPENED, fields)
        logger.info(TAG, Event.NOTIFICATION_OPENED, fields = fields)
    }

    object Event {
        const val NOTIFICATION_OPENED = "notification_opened"
    }

    object Key {
        const val KIND = "kind"
        const val TAP_ID = "tap_id"
    }

    private companion object {
        const val TAG = "APP"
    }
}

/** The app shell's events, declared so debug builds keep them. */
val appAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(
        AppTelemetry.Event.NOTIFICATION_OPENED,
        mapOf(AppTelemetry.Key.KIND to PropertyType.STRING, AppTelemetry.Key.TAP_ID to PropertyType.STRING),
    ),
)

internal val appTelemetryModule = module {
    single { AppTelemetry(analytics = get(), logger = get(), ids = get()) }
}
