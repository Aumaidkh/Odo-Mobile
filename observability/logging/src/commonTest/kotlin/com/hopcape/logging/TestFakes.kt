package com.hopcape.logging

import com.hopcape.logging.api.LogFileHandle
import com.hopcape.logging.api.LogFileStore
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.LogUploadResult
import com.hopcape.logging.api.DiagnosticRequests
import com.hopcape.logging.api.LogUploadTarget
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.logging.internal.file.InMemoryLogFileStore
import com.hopcape.logging.internal.model.LogEvent
import com.hopcape.logging.internal.sinks.LogSink
import com.hopcape.logging.internal.sinks.Sealable

/**
 * Test doubles shared across the logging test suite. They implement the module's
 * (internal) `LogSink` and public `Logger` ports so the real components can be
 * exercised without touching Logcat/files — the whole point of the port design.
 */

/** A [LogSink] that records every event and flush it receives. */
internal class RecordingSink : LogSink {
    val written = mutableListOf<LogEvent>()
    var flushCount = 0
        private set

    override fun write(event: LogEvent) {
        written += event
    }

    override fun flush() {
        flushCount++
    }
}

/** A [LogSink] that also implements [Sealable] — verifies `AsyncSink` seals its delegate on
 *  an explicit `flush()` and never on the automatic drain triggers. */
internal class RecordingSealableSink : LogSink, Sealable {
    val written = mutableListOf<LogEvent>()
    var flushCount = 0
        private set
    var sealCount = 0
        private set

    override fun write(event: LogEvent) {
        written += event
    }

    override fun flush() {
        flushCount++
    }

    override fun sealCurrentFile() {
        sealCount++
    }
}

/** A [LogSink] that always throws — to prove `SafeSink` isolates failures. */
internal class ThrowingSink(
    private val boom: Throwable = RuntimeException("sink boom"),
) : LogSink {
    override fun write(event: LogEvent): Unit = throw boom
    override fun flush(): Unit = throw boom
}

/** A [Logger] that records every call — for testing `ScopedLogger` in isolation. */
internal class RecordingLogger : Logger {
    data class Entry(
        val level: LogLevel,
        val tag: String,
        val event: String,
        val traceContext: TraceContext?,
        val fields: Map<String, Any?>,
    )

    val entries = mutableListOf<Entry>()
    var flushCount = 0
        private set

    val last: Entry get() = entries.last()

    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) {
        entries += Entry(level, tag, event, traceContext, fields)
    }

    override fun flush() {
        flushCount++
    }
}

/** A [LogFileStore] that counts [sealOrphans] calls — everything else delegates to a real
 *  [InMemoryLogFileStore]. Used to verify `LoggerFactory` seals orphans exactly once at
 *  construction (docs/LOGGING_PLAN.md's "session rotation" — see `RotationPolicy`'s doc). */
internal class RecordingLogFileStore(
    private val delegate: LogFileStore = InMemoryLogFileStore(nowMs = { 0L }),
) : LogFileStore by delegate {
    var sealOrphansCallCount = 0
        private set

    override fun sealOrphans(): List<LogFileHandle> {
        sealOrphansCallCount++
        return delegate.sealOrphans()
    }
}

/** A [LogUploadTarget] whose result per call is decided by [resultFor] — defaults to always
 *  delivering. Records every file it was asked to upload, in call order, and the diagnostics
 *  reference each was filed under. */
internal class FakeLogUploadTarget(
    private val resultFor: (LogFileHandle) -> LogUploadResult = { LogUploadResult.DELIVERED },
) : LogUploadTarget {
    override val name: String = "fake"
    val uploaded = mutableListOf<LogFileHandle>()
    val references = mutableListOf<String?>()

    override suspend fun upload(file: LogFileHandle, bytes: ByteArray, reference: String?): LogUploadResult {
        uploaded += file
        references += reference
        return resultFor(file)
    }
}

/** A [DiagnosticRequests] outbox in memory. Records what the coordinator did with the
 *  reference so a test can assert a partial pass did not close a request. */
internal class FakeDiagnosticRequests(private var pending: String? = null) : DiagnosticRequests {
    val delivered = mutableListOf<String>()
    val failed = mutableListOf<Pair<String, String?>>()

    override suspend fun open(reference: String, createdAtEpochMs: Long) {
        pending = reference
    }

    override suspend fun oldestOpen(): String? = pending

    override suspend fun markDelivered(reference: String) {
        delivered += reference
        pending = null
    }

    override suspend fun markAttemptFailed(reference: String, error: String?) {
        failed += reference to error
    }

    override suspend fun clearAll() {
        pending = null
    }
}
