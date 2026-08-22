package com.hopcape.odo.core.platform.sync

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.sync.SyncEngine
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncResult
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
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
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, noopTelemetry(), DEBOUNCE)

        // Logging three service entries in a row.
        repeat(3) { scheduler.requestSync(SyncReason.LocalWrite) }
        advanceTimeBy(SETTLE)

        assertEquals(1, engine.runs)
    }

    @Test
    fun theDebounceRestartsWithEachWrite() = runTest {
        val engine = CountingEngine()
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, noopTelemetry(), DEBOUNCE)

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
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, noopTelemetry(), DEBOUNCE)

        // Someone is watching a spinner.
        scheduler.requestSync(SyncReason.Manual)
        advanceTimeBy(1.seconds)

        assertEquals(1, engine.runs)
    }

    @Test
    fun startupDoesNotWaitEither() = runTest {
        val engine = CountingEngine()
        CoroutineSyncScheduler({ engine }, backgroundScope, noopTelemetry(), DEBOUNCE).scheduleStartupSync()
        advanceTimeBy(1.seconds)

        assertEquals(1, engine.runs)
    }

    @Test
    fun aRequestDuringARunIsDropped_notQueued() = runTest {
        val engine = CountingEngine()
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, noopTelemetry(), DEBOUNCE)

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
        val scheduler = CoroutineSyncScheduler({ engine }, backgroundScope, noopTelemetry(), DEBOUNCE)

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
            telemetry = noopTelemetry(),
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

    /**
     * A real [SyncTelemetry] over sinks that do nothing.
     *
     * Not a fake telemetry: the class is concrete and the scheduler calls it on every
     * request, so the honest way to keep it out of these assertions is to give it somewhere
     * quiet to write.
     */
    private fun noopTelemetry() = SyncTelemetry(NoopLogger, NoopAnalytics, NoopTracer, NoopCrash)

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit

        override fun flush() = Unit
    }

    private object NoopAnalytics : AnalyticsTracker {
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            object : Span {
                override val spanId = "span-$name"
                override val traceId = traceId
                override val parentSpanId = parentSpanId
                override val name = name
                override fun setAttribute(key: String, value: Any?): Span = this
            }

        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    private companion object {
        val DEBOUNCE = 5.seconds

        /** Comfortably past the debounce, so a pending run has certainly fired. */
        val SETTLE = 30.seconds
    }
}
