package com.hopcape.odo.core.data.appstatus

import com.hopcape.odo.core.data.appstatus.observability.AppStatusTelemetry
import com.hopcape.odo.core.domain.appstatus.AppAvailability
import com.hopcape.odo.core.domain.appstatus.AppStatus
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import com.hopcape.odo.core.domain.appstatus.AppStatusSource
import com.hopcape.odo.core.domain.appstatus.DEFAULT_MAINTENANCE_TRUST_WINDOW
import com.hopcape.odo.core.domain.appstatus.evaluateAvailability
import com.hopcape.odo.core.platform.app.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The only [AppStatusProvider]. Holds the last fetched [AppStatus] and re-derives
 * [availability] through [evaluateAvailability] whenever it changes or enough time has
 * passed for a maintenance verdict to go stale (D4, docs/APP_STATUS_PLAN.md §4.3).
 *
 * Starts at [AppAvailability.Allowed] — fail open — so the shell never has a "loading" gate
 * state to render; [refresh] then brings in whatever the remote actually says.
 *
 * Owns its own [scope] rather than borrowing one, same reasoning as
 * [com.hopcape.odo.core.data.car.PrimaryCarProvider]: the provider outlives every screen.
 * **Test callers must pass `TestScope.backgroundScope`** — the recheck loop below never
 * completes, and a plain `CoroutineScope` here makes `runTest` hang for real.
 */
internal class DefaultAppStatusProvider(
    private val source: AppStatusSource,
    private val appInfo: AppInfo,
    private val clock: Clock,
    private val telemetry: AppStatusTelemetry,
    private val trustWindow: Duration = DEFAULT_MAINTENANCE_TRUST_WINDOW,
    private val recheckInterval: Duration = DEFAULT_RECHECK_INTERVAL,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AppStatusProvider {

    private val mutex = Mutex()
    private var lastStatus: AppStatus = AppStatus.Unknown

    private val _availability = MutableStateFlow<AppAvailability>(AppAvailability.Allowed)
    override val availability: StateFlow<AppAvailability> = _availability.asStateFlow()

    init {
        // Decays a stale maintenance verdict back to Allowed on its own (D4) — the one case
        // a full-screen block with no other trigger would otherwise never release itself
        // from, because nothing else re-evaluates the policy while the app just sits there.
        scope.launch {
            while (true) {
                delay(recheckInterval)
                mutex.withLock { applyPolicyLocked() }
            }
        }
    }

    override suspend fun refresh() {
        val fetched = telemetry.refresh { source.fetch() }
        if (fetched == null) {
            telemetry.fetchFailed()
            return
        }
        mutex.withLock {
            lastStatus = fetched
            applyPolicyLocked()
        }
    }

    private fun applyPolicyLocked() {
        val previous = _availability.value
        val next = evaluateAvailability(lastStatus, appInfo.versionCode, clock, trustWindow)
        if (next == previous) return
        _availability.value = next
        reportTransition(previous, next)
    }

    private fun reportTransition(previous: AppAvailability, next: AppAvailability) {
        val wasBlocked = previous is AppAvailability.Blocked
        val isBlocked = next is AppAvailability.Blocked
        when {
            isBlocked && !wasBlocked -> telemetry.blocked(reasonOf(next as AppAvailability.Blocked))
            wasBlocked && !isBlocked -> telemetry.released()
        }
    }

    private fun reasonOf(blocked: AppAvailability.Blocked): String = when (blocked) {
        AppAvailability.Blocked.UpdateRequired -> AppStatusTelemetry.REASON_UPDATE_REQUIRED
        is AppAvailability.Blocked.Maintenance -> AppStatusTelemetry.REASON_MAINTENANCE
    }

    private companion object {
        /** Short enough that a stale block clears within about a minute of going stale. */
        val DEFAULT_RECHECK_INTERVAL = 1.minutes
    }
}
