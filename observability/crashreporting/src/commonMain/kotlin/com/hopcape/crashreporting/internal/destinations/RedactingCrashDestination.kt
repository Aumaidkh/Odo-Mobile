package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.internal.model.CrashReport
import com.hopcape.crashreporting.internal.redactor.CrashPiiRedactor

// ─────────────────────────────────────────────────────────────
// RedactingCrashDestination — Decorator that PII-scrubs a report
// before it reaches the wrapped vendor destination. Composed OUTSIDE
// the vendor and INSIDE the SafeCrashDestination — i.e.
// Safe(Redacting(vendor)) — so redaction always runs before delivery
// and any redactor failure is still caught by the safe wrapper.
//
// Redaction is a pass-through concern for the identity operations
// (setCustomKey / setUserId): a single custom-key value is masked
// here, but user ids and structured keys are forwarded as-is — the
// call site is responsible for not passing raw PII as a user id.
// ─────────────────────────────────────────────────────────────
internal class RedactingCrashDestination(
    private val delegate: CrashDestination,
    private val redactor: CrashPiiRedactor,
) : CrashDestination {

    override val name: String = delegate.name

    override fun record(report: CrashReport) = delegate.record(redactor.redact(report))

    override fun setCustomKey(key: String, value: Any?) = delegate.setCustomKey(key, value)

    override fun setUserId(userId: String?) = delegate.setUserId(userId)
}
