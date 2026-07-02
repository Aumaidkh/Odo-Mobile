package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// SafeCrashDestination — Decorator, the same fail-safe guarantee as
// the analytics SafeDestination / logger SafeSink. A vendor SDK
// throwing (network down, SDK not initialized, malformed payload)
// must NEVER crash the host or abort delivery to the *other*
// destinations — especially dangerous here, since we're often
// already handling a crash.
//
// Unlike the APM SafeSpanExporter, this SWALLOWS after reporting: a
// crash report has no dispatcher to retry it, so re-throwing would
// only endanger the (possibly already-dying) process.
// ─────────────────────────────────────────────────────────────
internal class SafeCrashDestination(
    private val delegate: CrashDestination,
    private val onError: (destinationName: String, error: Throwable) -> Unit,
) : CrashDestination {

    override val name: String = delegate.name

    override fun record(report: CrashReport) {
        runCatching { delegate.record(report) }.onFailure { onError(name, it) }
    }

    override fun setCustomKey(key: String, value: Any?) {
        runCatching { delegate.setCustomKey(key, value) }.onFailure { onError(name, it) }
    }

    override fun setUserId(userId: String?) {
        runCatching { delegate.setUserId(userId) }.onFailure { onError(name, it) }
    }
}
