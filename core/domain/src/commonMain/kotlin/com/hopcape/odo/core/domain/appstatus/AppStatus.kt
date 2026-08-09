package com.hopcape.odo.core.domain.appstatus

import kotlin.time.Instant

/** How hard a maintenance window bites. */
enum class MaintenanceSeverity {
    /** No maintenance in effect. */
    OFF,

    /** Network work stops; the local app keeps working. */
    DEGRADED,

    /** The app stops entirely until this clears. */
    FULL_BLOCK,
}

/**
 * The remote operator's answer to "can this build run right now" — one flat snapshot of
 * the three values in the remote contract (docs/APP_STATUS_PLAN.md §3), read as one unit
 * so a maintenance flag and its message can never disagree.
 *
 * Never used directly by a screen — [AppAvailabilityPolicy] turns it into an
 * [AppAvailability], which is the type everything outside `:core:data` actually sees.
 */
data class AppStatus(
    /** Builds with a lower `versionCode` are blocked. `0` blocks nothing. */
    val minSupportedVersionCode: Long,
    val maintenance: MaintenanceSeverity,
    /** Operator-supplied copy for the maintenance screen/banner. Blank means use the default. */
    val maintenanceMessage: String?,
    /** When these values were last confirmed from the remote source. Null = never fetched. */
    val fetchedAt: Instant?,
) {
    companion object {
        /** The fail-open value: blocks nothing, used before anything has ever been fetched. */
        val Unknown = AppStatus(
            minSupportedVersionCode = 0L,
            maintenance = MaintenanceSeverity.OFF,
            maintenanceMessage = null,
            fetchedAt = null,
        )
    }
}
