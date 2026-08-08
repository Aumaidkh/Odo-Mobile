package com.hopcape.logging.internal.sinks

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.model.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Batches events onto a single writer coroutine so the calling thread never blocks on disk
 * IO — the decorator between `RedactingSink` and `FileSink` in the file branch of the chain
 * (docs/LOGGING_PLAN.md §4–§5).
 *
 * `write`/`flush` are plain, non-`suspend` functions — required by the `LogSink` contract —
 * so they only ever enqueue onto [incoming], an unlimited, lock-free channel. Everything that
 * actually touches [buffer] (`enqueue`, eviction, [drain]) runs on the single consumer
 * coroutine in [runWriterLoop], so none of it needs its own locking: there is exactly one
 * thread of execution touching that state, by construction, not by discipline.
 *
 * Flushes to [delegate] on whichever of these comes first:
 * - the buffer reaches [flushAtEventCount] events or [flushAtBufferedBytes] bytes;
 * - an event at or above [immediateFlushLevel] arrives (default `WARN` — the event you need
 *   if the process dies right after it);
 * - [flushIntervalMs] elapses with no new event;
 * - an explicit [flush] call — which, unlike the other three, also seals [delegate]'s current
 *   file if it implements [Sealable] (see that interface's doc for why only this trigger does).
 *
 * Overflow — more than [maxBufferedEvents] events queued faster than they drain — drops the
 * **oldest** buffered event, never the newest, and counts the drops. The count surfaces as one
 * synthetic `logger_overflow` line prepended to the next batch that actually drains, so a gap
 * is visible in the file rather than silently missing.
 *
 * **Why this needs its own [onInternalError], separate from `SafeSink`'s:** `SafeSink` wraps
 * this class's `write`/`flush` — but those only enqueue onto [incoming] and return immediately.
 * The real work — calling [delegate]'s `write`/`flush`/`sealCurrentFile`, i.e. the actual disk
 * IO — happens later, inside [runWriterLoop], on this class's own coroutine. `SafeSink` never
 * sees that call stack. Without a guard here, a delegate that throws (a full disk, a
 * permission error) would kill this coroutine's `while (true)` outright — silently ending file
 * logging for the rest of the process, with nothing to say why.
 */
internal class AsyncSink(
    private val delegate: LogSink,
    private val maxBufferedEvents: Int = DEFAULT_MAX_BUFFERED_EVENTS,
    private val flushAtEventCount: Int = DEFAULT_FLUSH_AT_EVENT_COUNT,
    private val flushAtBufferedBytes: Long = DEFAULT_FLUSH_AT_BUFFERED_BYTES,
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    private val immediateFlushLevel: LogLevel = LogLevel.WARN,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onInternalError: (Throwable) -> Unit = {},
) : LogSink {

    private val incoming = Channel<WorkItem>(capacity = Channel.UNLIMITED)

    // Touched only from runWriterLoop's single coroutine — see the class doc.
    private val buffer = ArrayDeque<LogEvent>()
    private var bufferedBytes = 0L
    private var droppedCount = 0L

    private val writerJob: Job = scope.launch { runWriterLoop() }

    override fun write(event: LogEvent) {
        incoming.trySend(WorkItem.Write(event))
    }

    override fun flush() {
        incoming.trySend(WorkItem.FlushNow)
    }

    /** Stops the writer coroutine. Production has no reason to call this — `HLogger` lives
     *  for the process — but tests need it so a leaked loop can't outlive its test case. */
    fun shutdown() {
        writerJob.cancel()
        scope.cancel()
    }

    private suspend fun runWriterLoop() {
        while (true) {
            when (val item = withTimeoutOrNull(flushIntervalMs) { incoming.receive() }) {
                null -> if (buffer.isNotEmpty()) drain() // the interval elapsed with nothing new
                is WorkItem.Write -> {
                    enqueue(item.event)
                    if (shouldDrainNow(item.event)) drain()
                }
                WorkItem.FlushNow -> drain(sealAfterward = true)
            }
        }
    }

    private fun enqueue(event: LogEvent) {
        buffer.addLast(event)
        bufferedBytes += event.approxSizeBytes()
        while (buffer.size > maxBufferedEvents) {
            bufferedBytes -= buffer.removeFirst().approxSizeBytes()
            droppedCount++
        }
    }

    private fun shouldDrainNow(justEnqueued: LogEvent): Boolean =
        buffer.size >= flushAtEventCount ||
            bufferedBytes >= flushAtBufferedBytes ||
            justEnqueued.level.priority >= immediateFlushLevel.priority

    private fun drain(sealAfterward: Boolean = false) {
        if (droppedCount > 0) {
            buffer.addLast(overflowEvent(droppedCount))
            droppedCount = 0
        }
        if (buffer.isNotEmpty()) {
            val batch = buffer.toList()
            buffer.clear()
            bufferedBytes = 0L
            runGuarded { batch.forEach(delegate::write) }
            runGuarded { delegate.flush() }
        }
        // Independent of whether this drain had anything new: a file opened by an earlier
        // drain and never rotated since must still seal on an explicit flush.
        if (sealAfterward && delegate is Sealable) runGuarded { delegate.sealCurrentFile() }
    }

    /** Keeps [runWriterLoop]'s `while (true)` alive across a misbehaving [delegate] — see
     *  the class doc for why `SafeSink` cannot be the one doing this. */
    private inline fun runGuarded(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            onInternalError(t)
        }
    }

    private fun overflowEvent(dropped: Long): LogEvent =
        LogEvent.Builder(OVERFLOW_TAG, OVERFLOW_EVENT)
            .level(LogLevel.WARN)
            .field("dropped", dropped)
            .build()

    private sealed interface WorkItem {
        data class Write(val event: LogEvent) : WorkItem
        data object FlushNow : WorkItem
    }

    private companion object {
        const val DEFAULT_MAX_BUFFERED_EVENTS = 512
        const val DEFAULT_FLUSH_AT_EVENT_COUNT = 64
        const val DEFAULT_FLUSH_AT_BUFFERED_BYTES = 32L * 1024
        const val DEFAULT_FLUSH_INTERVAL_MS = 5_000L
        const val OVERFLOW_TAG = "Logger"
        const val OVERFLOW_EVENT = "logger_overflow"

        /** A cheap estimate, not the real serialized size — good enough to decide when to
         *  flush without duplicating FileSink's JSON encoding on every buffered event. */
        fun LogEvent.approxSizeBytes(): Int {
            val fieldsSize = fields.entries.sumOf { (k, v) -> k.length + (v?.toString()?.length ?: 4) }
            return APPROX_OVERHEAD_BYTES + tag.length + event.length + fieldsSize
        }

        const val APPROX_OVERHEAD_BYTES = 48
    }
}
