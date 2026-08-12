package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// CrashDestination — Strategy, one per vendor SDK (Crashlytics,
// Sentry, …). The reporter never imports a vendor SDK directly
// (DIP); it only knows this interface. Adding or swapping a vendor
// is a new class here, zero changes elsewhere (OCP). The analog of
// the analytics module's AnalyticsDestination.
// ─────────────────────────────────────────────────────────────
internal interface CrashDestination {
    /** Stable name for diagnostics/dead-letter messages. */
    val name: String

    /** Delivers a (already PII-scrubbed) report. May throw — decorators isolate failures. */
    fun record(report: CrashReport)

    /** Sets a custom key on the vendor's session, applied to future reports. */
    fun setCustomKey(key: String, value: Any?)

    /** Associates the vendor's session with a user identity. */
    fun setUserId(userId: String?)
}
