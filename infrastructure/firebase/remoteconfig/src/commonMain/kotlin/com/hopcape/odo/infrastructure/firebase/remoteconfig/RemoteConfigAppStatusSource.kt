package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.domain.appstatus.AppStatus
import com.hopcape.odo.core.domain.appstatus.AppStatusSource
import com.hopcape.odo.core.domain.appstatus.MaintenanceSeverity

/**
 * [AppStatusSource] backed by Firebase Remote Config — the three flat keys in
 * docs/APP_STATUS_PLAN.md §3.
 *
 * Every failure mode collapses to `null` (fail open, per [AppStatusSource]'s contract):
 * an unconfigured build, a throttled fetch on a fresh install with nothing cached yet, or
 * any SDK error [FirebaseRemoteConfigGateway] already swallowed. [DefaultAppStatusProvider]
 * treats a `null` fetch as "keep the previous verdict", which for a build that has never
 * once reached Firebase is the fail-open [AppStatus.Unknown].
 */
internal class RemoteConfigAppStatusSource(
    private val gateway: FirebaseRemoteConfigGateway,
) : AppStatusSource {

    override suspend fun fetch(): AppStatus? {
        // The return value only says whether a *new* config was just activated — ignored
        // here because long()/string() below read whatever is currently in force either
        // way, and lastFetchAt is what actually decides freshness.
        gateway.fetchAndActivate()

        val fetchedAt = gateway.lastFetchAt ?: return null
        return AppStatus(
            minSupportedVersionCode = gateway.long(KEY_MIN_SUPPORTED_VERSION_CODE) ?: DEFAULT_MIN_VERSION_CODE,
            maintenance = parseMaintenance(gateway.string(KEY_MAINTENANCE_MODE)),
            maintenanceMessage = gateway.string(KEY_MAINTENANCE_MESSAGE)?.trim()?.takeIf { it.isNotEmpty() },
            fetchedAt = fetchedAt,
        )
    }

    /** Anything unrecognised — including a typo in the console — fails open to [MaintenanceSeverity.OFF]. */
    private fun parseMaintenance(raw: String?): MaintenanceSeverity = when (raw?.trim()?.lowercase()) {
        VALUE_DEGRADED -> MaintenanceSeverity.DEGRADED
        VALUE_FULL_BLOCK -> MaintenanceSeverity.FULL_BLOCK
        else -> MaintenanceSeverity.OFF
    }

    internal companion object {
        const val KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
        const val KEY_MAINTENANCE_MODE = "maintenance_mode"
        const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"

        const val VALUE_OFF = "off"
        const val VALUE_DEGRADED = "degraded"
        const val VALUE_FULL_BLOCK = "full_block"

        private const val DEFAULT_MIN_VERSION_CODE = 0L

        /** [FirebaseRemoteConfigGateway]'s `setDefaults` — the safe, non-blocking answer a fresh install has before any network. */
        val REMOTE_DEFAULTS: Map<String, Any> = mapOf(
            KEY_MIN_SUPPORTED_VERSION_CODE to DEFAULT_MIN_VERSION_CODE,
            KEY_MAINTENANCE_MODE to VALUE_OFF,
            KEY_MAINTENANCE_MESSAGE to "",
        )
    }
}
