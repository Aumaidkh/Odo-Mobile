package com.hopcape.odo.infrastructure.database.settings

import com.hopcape.odo.infrastructure.database.db.App_settings
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.NotificationSchedule
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
    notificationSchedule = NotificationSchedule(
        documentLeadDays = notif_doc_leads.toLeadDays(),
        notifyAtHour = notif_hour.toInt().coerceIn(0, 23),
    ),
    trackerEnabled = tracker_enabled.toBoolean(),
    autoOdoPausedUntil = auto_odo_paused_until?.let { runCatching { Instant.parse(it) }.getOrNull() },
    aoLastAckedTripEndedAt = ao_last_acked_trip_ended_at?.let { runCatching { Instant.parse(it) }.getOrNull() },
)

/**
 * `INSURANCE=30,7,1;PUC=15,3` — the owner's leads, per kind of paper.
 *
 * Written and read here rather than as JSON: the value is a handful of numbers, and a
 * column a person can read in a database browser is worth more than a serializer for it.
 * Anything unparseable is dropped rather than guessed — a type Odo no longer knows, or a
 * row written by a newer build, falls back to the product's own table.
 */
internal fun String.toLeadDays(): Map<DocumentType, List<Int>> = split(LEAD_ENTRY_SEPARATOR)
    .filter { it.isNotBlank() }
    .mapNotNull { entry ->
        val name = entry.substringBefore(LEAD_TYPE_SEPARATOR)
        val type = DocumentType.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        val days = entry.substringAfter(LEAD_TYPE_SEPARATOR, missingDelimiterValue = "")
            .split(LEAD_DAY_SEPARATOR)
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
        type to days
    }
    .toMap()

/** The inverse of [toLeadDays]; an empty map stores as an empty string. */
internal fun Map<DocumentType, List<Int>>.asStoredLeadDays(): String =
    entries.joinToString(separator = LEAD_ENTRY_SEPARATOR.toString()) { (type, days) ->
        "${type.name}$LEAD_TYPE_SEPARATOR${days.joinToString(LEAD_DAY_SEPARATOR.toString())}"
    }

private const val LEAD_ENTRY_SEPARATOR = ';'
private const val LEAD_TYPE_SEPARATOR = '='
private const val LEAD_DAY_SEPARATOR = ','

private inline fun <reified T : Enum<T>> String.toEnum(fallback: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: fallback

private fun Long.toBoolean(): Boolean = this != 0L

/** SQLite has no boolean type; the columns are 0/1 integers. */
internal fun Boolean.toLong(): Long = if (this) 1L else 0L
