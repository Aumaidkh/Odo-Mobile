package com.hopcape.odo.core.domain.appstatus

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How long a fetched [AppStatus] is trusted to still describe reality (docs/APP_STATUS_PLAN.md §4.3). */
val DEFAULT_MAINTENANCE_TRUST_WINDOW: Duration = 30.minutes

/**
 * The one place every app-availability rule lives. Pure and dependency-free by design —
 * every case in docs/APP_STATUS_PLAN.md §9's test table is a call to this function.
 *
 * Order matters:
 * 1. A version below the remote minimum blocks regardless of how stale [status] is — the
 *    running build's version is a local fact, so a cached answer is still a correct one.
 * 2. A maintenance verdict is only honoured while [status] was fetched recently. Odo is
 *    offline-first; a maintenance flag nobody has reconfirmed within [maintenanceTrustWindow]
 *    must not hold an owner who went offline mid-window hostage forever.
 * 3. Otherwise the maintenance severity decides directly.
 */
fun evaluateAvailability(
    status: AppStatus,
    currentVersionCode: Long,
    now: Instant,
    maintenanceTrustWindow: Duration = DEFAULT_MAINTENANCE_TRUST_WINDOW,
): AppAvailability {
    if (currentVersionCode < status.minSupportedVersionCode) {
        return AppAvailability.Blocked.UpdateRequired
    }

    val fetchedAt = status.fetchedAt
    val maintenanceIsStale = fetchedAt == null || now - fetchedAt > maintenanceTrustWindow
    if (maintenanceIsStale) {
        return AppAvailability.Allowed
    }

    return when (status.maintenance) {
        MaintenanceSeverity.FULL_BLOCK -> AppAvailability.Blocked.Maintenance(status.maintenanceMessage)
        MaintenanceSeverity.DEGRADED -> AppAvailability.DegradedByMaintenance(status.maintenanceMessage)
        MaintenanceSeverity.OFF -> AppAvailability.Allowed
    }
}

/** Convenience overload reading the current instant from [clock]. */
fun evaluateAvailability(
    status: AppStatus,
    currentVersionCode: Long,
    clock: Clock,
    maintenanceTrustWindow: Duration = DEFAULT_MAINTENANCE_TRUST_WINDOW,
): AppAvailability = evaluateAvailability(status, currentVersionCode, clock.now(), maintenanceTrustWindow)
