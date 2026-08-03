package com.hopcape.odo.core.platform.sync

import com.hopcape.odo.core.sync.SyncEngine
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * The scheduler's job is deciding when *not* to run. Both behaviours below exist to stop a
 * burst of local writes turning into a burst of syncs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineSyncSchedulerTest {

    @Test
    fun aBurstOfLocalWritesBecomesOneRun() = runTest {
        val engine = CountingEngine()
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, DEBOUNCE)

        // Logging three service entries in a row.
        repeat(3) { scheduler.requestSync(SyncReason.LocalWrite) }
        advanceTimeBy(SETTLE)

        assertEquals(1, engine.runs)
    }

    @Test
    fun theDebounceRestartsWithEachWrite() = runTest {
        val engine = CountingEngine()
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, DEBOUNCE)

        scheduler.requestSync(SyncReason.LocalWrite)
        advanceTimeBy(3.seconds)
        // Still typing — the timer should start over rather than fire in two more seconds.
        scheduler.requestSync(SyncReason.LocalWrite)
        advanceTimeBy(3.seconds)

        assertEquals(0, engine.runs, "the second write should have reset the wait")

        advanceTimeBy(SETTLE)
        assertEquals(1, engine.runs)
    }

    @Test
    fun aManualRefreshDoesNotWait() = runTest {
        val engine = CountingEngine()
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, DEBOUNCE)

        // Someone is watching a spinner.
        scheduler.requestSync(SyncReason.Manual)
        advanceTimeBy(1.seconds)

        assertEquals(1, engine.runs)
    }

    @Test
    fun startupDoesNotWaitEither() = runTest {
        val engine = CountingEngine()
        CoroutineSyncScheduler({ engine }, backgroundScope, DEBOUNCE).scheduleStartupSync()
        advanceTimeBy(1.seconds)

        assertEquals(1, engine.runs)
    }

    @Test
    fun aRequestDuringARunIsDropped_notQueued() = runTest {
        val engine = CountingEngine()
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, DEBOUNCE)

        scheduler.requestSync(SyncReason.Manual)
        advanceTimeBy(SETTLE)          // the run has started and is blocked on `gate`

        scheduler.requestSync(SyncReason.Manual)
        advanceTimeBy(SETTLE)

        // Two concurrent runs would push the same rows twice and race on the cursor. A row
        // written mid-run is simply picked up by the next one.
        assertEquals(1, engine.runs)

        engine.gate.complete(Unit)
        advanceTimeBy(SETTLE)
    }

    @Test
    fun aNewRequestAfterOneFinishesDoesRun() = runTest {
        val engine = CountingEngine()
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, DEBOUNCE)

        scheduler.requestSync(SyncReason.Manual)
        advanceTimeBy(SETTLE)
        engine.gate.complete(Unit)
        advanceTimeBy(SETTLE)

        engine.gate = CompletableDeferred<Unit>().apply { complete(Unit) }
        scheduler.requestSync(SyncReason.Manual)
        advanceTimeBy(SETTLE)

        assertEquals(2, engine.runs)
    }

    @Test
    fun theEngineIsNotBuiltUntilARunHappens() = runTest {
        var built = 0
        val scheduler = CoroutineSyncScheduler(
            engine = { built++; CountingEngine().also { it.gate.complete(Unit) } },
            scope = backgroundScope,
            debounce = DEBOUNCE,
        )

        // Constructing the scheduler must not pull in the engine — that would build every
        // repository, and the database with them, on the startup thread.
        assertEquals(0, built)

        scheduler.requestSync(SyncReason.Manual)
        advanceTimeBy(SETTLE)
        assertEquals(1, built)
    }

    private class CountingEngine : SyncEngine {
        var runs = 0
        var gate = CompletableDeferred<Unit>()

        override suspend fun sync(): SyncResult {
            runs++
            gate.await()
            return SyncResult.Success
        }
    }

    private companion object {
        val DEBOUNCE = 5.seconds

        /** Comfortably past the debounce, so a pending run has certainly fired. */
        val SETTLE = 30.seconds
    }
}
