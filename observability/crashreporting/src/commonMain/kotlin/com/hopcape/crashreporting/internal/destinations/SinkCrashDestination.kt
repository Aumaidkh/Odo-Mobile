package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.api.CrashSink
import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// SinkCrashDestination — Adapter from the public CrashSink port to
// the internal CrashDestination pipeline shape, so an external sink
// is wrapped in Safe(Redacting(...)) exactly like a built-in
// destination. Keeps CrashReport internal: a sink only ever sees the
// resolved throwable/breadcrumb/key values, never the report model.
// The analog of the analytics module's SinkDestination.
// ─────────────────────────────────────────────────────────────
internal class SinkCrashDestination(
    private val sink: CrashSink,
) : CrashDestination {

    override val name: String = sink.name

    override fun record(report: CrashReport) = sink.record(
        throwableType = report.throwableType,
        throwableMessage = report.throwableMessage,
        stackTrace = report.stackTrace,
        isFatal = report.isFatal,
        breadcrumbs = report.breadcrumbs.map { "${it.tag}: ${it.message}" },
        customKeys = report.customKeys,
    )

    override fun setCustomKey(key: String, value: Any?) = sink.setCustomKey(key, value)

    override fun setUserId(userId: String?) = sink.setUserId(userId)
}
