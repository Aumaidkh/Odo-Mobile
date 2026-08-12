package com.hopcape.odo.infrastructure.database.settings

import com.hopcape.odo.infrastructure.database.db.App_settings
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.PrivacyPreferences
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit
import kotlin.time.Instant

/**
 * DB row → domain. Domain never sees the row type.
 *
 * An unreadable enum value reads as the default rather than crashing: settings are a
 * preference, and a row written by a newer build must not stop the app from starting.
 */
internal fun App_settings.toDomain(): AppSettings = AppSettings(
    theme = theme.toEnum(ThemePreference.SYSTEM),
    largerText = larger_text.toBoolean(),
    distanceUnit = distance_unit.toEnum(DistanceUnit.Default),
    fuelEfficiencyUnit = fuel_efficiency_unit.toEnum(FuelEfficiencyUnit.Default),
    notifications = NotificationPreferences(
        documentExpiry = notif_doc_expiry.toBoolean(),
        serviceDue = notif_service_due.toBoolean(),
        customReminders = notif_custom.toBoolean(),
        overchargeAlerts = notif_overcharge.toBoolean(),
        monthlySummary = notif_monthly.toBoolean(),
        healthScoreDrops = notif_health_drop.toBoolean(),
        partnerOffers = notif_partner.toBoolean(),
        push = notif_push.toBoolean(),
        whatsapp = notif_whatsapp.toBoolean(),
    ),
    privacy = PrivacyPreferences(
        keepTripRoutes = privacy_keep_trip_routes.toBoolean(),
        usageAnalytics = privacy_usage_analytics.toBoolean(),
    ),
    trackerEnabled = tracker_enabled.toBoolean(),
    autoOdoPausedUntil = auto_odo_paused_until?.let { runCatching { Instant.parse(it) }.getOrNull() },
    aoLastAckedTripEndedAt = ao_last_acked_trip_ended_at?.let { runCatching { Instant.parse(it) }.getOrNull() },
)

private inline fun <reified T : Enum<T>> String.toEnum(fallback: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: fallback

private fun Long.toBoolean(): Boolean = this != 0L

/** SQLite has no boolean type; the columns are 0/1 integers. */
internal fun Boolean.toLong(): Long = if (this) 1L else 0L
