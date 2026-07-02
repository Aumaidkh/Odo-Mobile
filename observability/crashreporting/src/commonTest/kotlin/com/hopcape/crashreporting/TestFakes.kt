package com.hopcape.crashreporting

import com.hopcape.crashreporting.api.DeviceContext
import com.hopcape.crashreporting.internal.destinations.CrashDestination
import com.hopcape.crashreporting.internal.model.Breadcrumb
import com.hopcape.crashreporting.internal.model.CrashReport
import com.hopcape.crashreporting.internal.store.CrashFileStore

/**
 * Test doubles + builders shared across the crash-reporting suite. They implement
 * the module's (internal) [CrashDestination] and [CrashFileStore] ports so the
 * real orchestration can be exercised without a live vendor backend or disk —
 * the point of the ports.
 */

internal fun testDeviceContext(): DeviceContext = DeviceContext(
    appVersion = "1.0.0",
    osVersion = "Android 14",
    deviceModel = "Pixel-Test",
    availableMemoryMb = 512,
    batteryLevel = 78,
    networkType = "wifi",
)

/** Builds a minimal [CrashReport] for tests. */
internal fun testReport(
    crashId: String = "crash-1",
    isFatal: Boolean = false,
    throwableType: String = "IllegalStateException",
    throwableMessage: String? = "boom",
    breadcrumbs: List<Breadcrumb> = emptyList(),
    customKeys: Map<String, Any?> = emptyMap(),
): CrashReport = CrashReport(
    crashId = crashId,
    timestampMs = 1_700_000_000_000L,
    throwableType = throwableType,
    throwableMessage = throwableMessage,
    stackTrace = "$throwableType: $throwableMessage\n\tat Foo.bar(Foo.kt:1)",
    isFatal = isFatal,
    breadcrumbs = breadcrumbs,
    customKeys = customKeys,
    deviceContext = testDeviceContext(),
    traceId = "trace-1",
    sessionId = "session-1",
)

/**
 * A [CrashDestination] that records everything it receives. [failRecordTimes]
 * makes the first N `record` calls throw, so fail-safe isolation can be exercised.
 */
internal class RecordingCrashDestination(
    override val name: String = "recording",
    private var failRecordTimes: Int = 0,
) : CrashDestination {

    val recorded = mutableListOf<CrashReport>()
    val customKeys = mutableListOf<Pair<String, Any?>>()
    var userId: String? = null
        private set

    override fun record(report: CrashReport) {
        if (failRecordTimes > 0) {
            failRecordTimes--
            throw RuntimeException("destination boom")
        }
        recorded += report
    }

    override fun setCustomKey(key: String, value: Any?) {
        customKeys += key to value
    }

    override fun setUserId(userId: String?) {
        this.userId = userId
    }
}

/** A [CrashFileStore] backed by a plain map — deterministic for assertions. */
internal class RecordingCrashFileStore : CrashFileStore {
    val written = linkedMapOf<String, CrashReport>()
    val cleared = mutableListOf<String>()

    override fun writeSync(report: CrashReport) {
        written[report.crashId] = report
    }

    override fun readPending(): List<CrashReport> = written.values.toList()

    override fun clear(crashId: String) {
        cleared += crashId
        written.remove(crashId)
    }
}
