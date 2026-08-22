package com.hopcape.odo.core.data.appstatus

import com.hopcape.odo.core.domain.appstatus.AppAvailability
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import com.hopcape.odo.core.sync.SyncGate
import com.hopcape.odo.core.sync.SyncVerdict

/**
 * Decorates the session [SyncGate] with the app-status gate: a run is allowed only while
 * both agree. A maintenance window — `DEGRADED` or `FULL_BLOCK`, checked without
 * distinguishing them, since sync stands down for either — closes the gate before the
 * session gate's own check (and its adoption side effect) ever runs, so nothing pushes into
 * a backend mid-migration.
 *
 * The refusal is [SyncVerdict.Unavailable] rather than [SyncVerdict.NoSession], because a
 * maintenance window ends. Reporting it as a finished run would drop whatever was scheduled
 * — including the first pull after a sign-in — and nothing would ask again.
 *
 * Not a change to [SyncGate] or the engine itself — the engine keeps being handed its
 * answer, same as always; this is only a different answer.
 *
 * Bound as the *unqualified* `SyncGate` in `coreDataModule`, wrapping the session gate under
 * its own qualifier — see that module for why a plain second `single<SyncGate>` would have
 * resolved itself instead of wrapping it.
 */
internal class MaintenanceAwareSyncGate(
    private val session: SyncGate,
    private val appStatus: AppStatusProvider,
) : SyncGate {

    override suspend fun evaluate(): SyncVerdict {
        if (appStatus.availability.value !is AppAvailability.Allowed) {
            return SyncVerdict.Unavailable(MAINTENANCE)
        }
        return session.evaluate()
    }

    private companion object {
        const val MAINTENANCE = "maintenance window"
    }
}
