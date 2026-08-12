package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.SyncReason

/**
 * Accepts every request and schedules nothing — the stand-in until the platform scheduler
 * arrives (WorkManager on Android, `BGTaskScheduler` on iOS in Phase 2).
 *
 * This is not a placeholder that lies. `requestSync` means "there is something worth
 * pushing"; a device with no engine behind it genuinely has nothing to schedule, so doing
 * nothing is the correct behaviour rather than a stub for it. The local write has already
 * landed and the row is `PENDING` — it waits, which is exactly what offline-first means.
 *
 * Wiring it now is the point: the call sites are the part that is easy to forget, and a
 * missing `requestSync` after a delete is invisible until the day someone wonders why a
 * deletion never reached their other device. With these in place, M5 implements one class
 * and swaps one Koin line.
 */
internal class NoopSyncScheduler : SyncScheduler {
    override fun scheduleStartupSync() = Unit
    override fun requestSync(reason: SyncReason) = Unit
}
