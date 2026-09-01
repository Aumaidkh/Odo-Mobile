package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.domain.appstatus.AppStatus
import com.hopcape.odo.core.domain.appstatus.AppStatusSource

/**
 * [AppStatusSource] backed by Firebase Remote Config — the three keys in [AppStatusConfig].
 *
 * Every failure mode collapses to `null` (fail open, per [AppStatusSource]'s contract):
 * an unconfigured build, a throttled fetch on a fresh install with nothing cached yet, or
 * any SDK error [FirebaseRemoteConfigGateway] already swallowed. `DefaultAppStatusProvider`
 * treats a `null` fetch as "keep the previous verdict", which for a build that has never
 * once reached Firebase is the fail-open `AppStatus.Unknown`.
 *
 * The fetch goes through [ConfigRefresher] rather than calling the gateway directly.
 * Fetching on the gateway would activate new values without bumping the generation counter,
 * so any screen observing a config flow would keep showing the previous answer until
 * something else happened to fetch.
 *
 * An unrecognised `maintenance_mode` — a typo in the console, or a value from a later
 * release — still fails open to [com.hopcape.odo.core.domain.appstatus.MaintenanceSeverity.OFF]:
 * the resolver only accepts a name the declaration lists, and falls back to the compiled
 * default otherwise.
 */
internal class RemoteConfigAppStatusSource(
    private val gateway: FirebaseRemoteConfigGateway,
    private val config: AppStatusConfig,
    private val refresher: ConfigRefresher,
) : AppStatusSource {

    override suspend fun fetch(): AppStatus? {
        refresher.refresh()

        // Not "now": the SDK's own record of its last *successful* network fetch, which is
        // what actually decides whether there is anything to report.
        val fetchedAt = gateway.lastFetchAt ?: return null
        return AppStatus(
            minSupportedVersionCode = config.minSupportedVersionCode,
            maintenance = config.maintenance,
            maintenanceMessage = config.maintenanceMessage.takeIf { it.isNotEmpty() },
            fetchedAt = fetchedAt,
        )
    }
}
