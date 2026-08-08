@file:OptIn(ExperimentalCoroutinesApi::class)

package com.hopcape.logging.internal.sinks

import com.hopcape.logging.RecordingSealableSink
import com.hopcape.logging.RecordingSink
import com.hopcape.logging.ThrowingSink
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.model.LogEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsyncSinkTest {

    private fun event(level: LogLevel = LogLevel.INFO, name: String = "e") =
        LogEvent.Builder("T", name).level(level).build()

    /**
     * A sink whose thresholds only the dimension under test can cross, on a deterministic
     * test dispatcher so writes and drains happen on the calling thread's call stack.
     *
     * Scoped to [TestScope.backgroundScope], not a bare `CoroutineScope(UnconfinedTestDispatcher(...))`
     * — `AsyncSink`'s writer loop never terminates on its own (`while (true)`), and `runTest`
     * winding down tries to drain every *other* kind of scope to idle before finishing. Against
     * an infinite loop that keeps rescheduling its own delay, that drain never converges — a
     * real, reproduced multi-minute hang, not a hypothetical one. `backgroundScope` is
     * `kotlinx-coroutines-test`'s answer to exactly this shape (a worker that outlives the
     * test body but must not block `runTest` completing): it cancels automatically, and
     * `runTest` does not wait for it.
     */
    private fun TestScope.sink(
        delegate: LogSink,
        maxBufferedEvents: Int = 100,
        flushAtEventCount: Int = 100,
        flushAtBufferedBytes: Long = 1_000_000L,
        flushIntervalMs: Long = 5_000L,
        immediateFlushLevel: LogLevel = LogLevel.ERROR,
    ) = AsyncSink(
        delegate = delegate,
        maxBufferedEvents = maxBufferedEvents,
        flushAtEventCount = flushAtEventCount,
        flushAtBufferedBytes = flushAtBufferedBytes,
        flushIntervalMs = flushIntervalMs,
        immediateFlushLevel = immediateFlushLevel,
        scope = backgroundScope,
    )

    @Test
    fun write_returnsImmediately_delegateSeesNothingUntilATriggerFires() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingSink()
        val asyncSink = sink(delegate)

        asyncSink.write(event(name = "still-buffered"))

        assertTrue(delegate.written.isEmpty(), "must not drain before a threshold or the interval")
    }

    @Test
    fun flushAtEventCount_drainsAutomaticallyOnceReached() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingSink()
        val asyncSink = sink(delegate, flushAtEventCount = 3)

        asyncSink.write(event(name = "a"))
        asyncSink.write(event(name = "b"))
        assertTrue(delegate.written.isEmpty(), "below the 3-event threshold")

        asyncSink.write(event(name = "c"))

        assertEquals(listOf("a", "b", "c"), delegate.written.map { it.event })
        assertEquals(1, delegate.flushCount)
    }

    @Test
    fun flushAtBufferedBytes_drainsAutomaticallyOnceReached() = runTest(UnconfinedTestDispatcher()) {
        // approxSizeBytes has a fixed ~49-byte floor per event (see AsyncSink's overhead
        // constant), so one short event stays under a 60-byte cap and a second tips it over.
        val delegate = RecordingSink()
        val asyncSink = sink(delegate, flushAtBufferedBytes = 60L)

        asyncSink.write(event(name = "a"))
        assertTrue(delegate.written.isEmpty(), "below the byte threshold")

        asyncSink.write(event(name = "bb"))

        assertEquals(listOf("a", "bb"), delegate.written.map { it.event })
    }

    @Test
    fun immediateFlushLevel_drainsRightAway_regardlessOfSizeThresholds() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingSink()
        val asyncSink = sink(delegate, immediateFlushLevel = LogLevel.WARN)

        asyncSink.write(event(LogLevel.WARN, name = "needs-attention"))

        assertEquals(listOf("needs-attention"), delegate.written.map { it.event })
    }

    @Test
    fun flushInterval_drainsAfterTimeElapses_evenBelowEveryOtherThreshold() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingSink()
        val asyncSink = sink(delegate, flushIntervalMs = 5_000L)

        asyncSink.write(event(name = "still-buffered"))
        assertTrue(delegate.written.isEmpty())

        testScheduler.advanceTimeBy(5_001L)
        testScheduler.runCurrent()

        assertEquals(listOf("still-buffered"), delegate.written.map { it.event })
    }

    @Test
    fun explicitFlush_drainsWhateverIsBuffered() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingSink()
        val asyncSink = sink(delegate)

        asyncSink.write(event(name = "a"))
        asyncSink.write(event(name = "b"))
        assertTrue(delegate.written.isEmpty())

        asyncSink.flush()

        assertEquals(listOf("a", "b"), delegate.written.map { it.event })
    }

    @Test
    fun overflow_dropsOldestEvents_andPrependsASyntheticLineToTheNextDrain() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingSink()
        val asyncSink = sink(delegate, maxBufferedEvents = 2)

        // Five writes into a 2-event cap: 3 must be dropped, oldest first.
        asyncSink.write(event(name = "e0"))
        asyncSink.write(event(name = "e1"))
        asyncSink.write(event(name = "e2"))
        asyncSink.write(event(name = "e3"))
        asyncSink.write(event(name = "e4"))
        assertTrue(delegate.written.isEmpty(), "no drain trigger fired yet")

        asyncSink.flush()

        val names = delegate.written.map { it.event }
        assertEquals(listOf("e3", "e4", "logger_overflow"), names)
        assertEquals(3L, delegate.written.last().fields["dropped"])
    }

    @Test
    fun explicitFlush_sealsASealableDelegate() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingSealableSink()
        val asyncSink = sink(delegate)

        asyncSink.write(event(name = "a"))
        asyncSink.flush()

        assertEquals(1, delegate.sealCount)
    }

    @Test
    fun automaticDrainTriggers_neverSealASealableDelegate() = runTest(UnconfinedTestDispatcher()) {
        val delegate = RecordingSealableSink()
        // Every automatic trigger tight enough to fire on the writes below.
        val asyncSink = sink(delegate, flushAtEventCount = 1, immediateFlushLevel = LogLevel.INFO)

        asyncSink.write(event(name = "a"))
        asyncSink.write(event(name = "b"))
        testScheduler.advanceTimeBy(5_001L)
        testScheduler.runCurrent()

        assertEquals(2, delegate.written.size, "drains must still have happened")
        assertEquals(0, delegate.sealCount, "only an explicit flush() may seal the file")
    }

    @Test
    fun explicitFlush_sealsEvenWhenNothingNewIsBuffered() = runTest(UnconfinedTestDispatcher()) {
        // A file opened by an earlier drain, with nothing new since, must still seal —
        // the coordinator preparing to upload cares about what's already on disk, not
        // about whether this particular flush() had fresh events.
        val delegate = RecordingSealableSink()
        val asyncSink = sink(delegate)
        asyncSink.write(event(name = "a"))
        asyncSink.flush()
        assertEquals(1, delegate.sealCount)

        asyncSink.flush() // nothing new since the last flush

        assertEquals(2, delegate.sealCount)
    }

    @Test
    fun aThrowingDelegate_doesNotKillTheWriterLoop_andReportsEveryFailure() = runTest(UnconfinedTestDispatcher()) {
        // SafeSink cannot catch this — the delegate call happens later, inside this class's
        // own writer coroutine, not on SafeSink's call stack (see the class doc). Without its
        // own guard, the first failure would silently end file logging for the rest of the
        // process: the `while (true)` loop dies and nothing restarts it.
        //
        // ThrowingSink throws on every call, and write/flush are guarded separately (they are
        // independent failure signals worth reporting on their own) — so each drain below
        // reports two errors, not one.
        val errors = mutableListOf<Throwable>()
        val asyncSink = AsyncSink(
            delegate = ThrowingSink(),
            scope = backgroundScope,
            onInternalError = { errors += it },
        )

        asyncSink.write(event(name = "a"))
        asyncSink.flush()
        asyncSink.write(event(name = "b"))
        asyncSink.flush()

        assertEquals(4, errors.size, "the loop must survive the first failure and keep reporting")
    }
}
