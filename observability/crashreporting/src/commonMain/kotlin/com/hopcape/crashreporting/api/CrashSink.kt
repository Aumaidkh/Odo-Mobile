package com.hopcape.crashreporting.api

// ─────────────────────────────────────────────────────────────
// CrashSink — the port an outside module implements to add a vendor
// destination (e.g. Firebase Crashlytics) without this module
// depending on that vendor's SDK. Public and deliberately smaller
// than the internal CrashDestination: a sink never sees the internal
// CrashReport model, only the resolved values it needs to forward.
// The analog of the analytics module's AnalyticsSink.
//
// A sink registered via CrashConfig.destinations is wrapped in the
// same Safe(Redacting(...)) decorator chain as every built-in
// destination, so a throwing sink can't crash the host or block
// delivery to the others — and every value it receives here is
// already PII-redacted.
// ─────────────────────────────────────────────────────────────
interface CrashSink {
    val name: String

    /**
     * Records a report. [breadcrumbs] are pre-formatted "tag: message" lines,
     * oldest first.
     */
    fun record(
        throwableType: String,
        throwableMessage: String?,
        stackTrace: String,
        isFatal: Boolean,
        breadcrumbs: List<String>,
        customKeys: Map<String, Any?>,
    )

    /** Sets a custom key attached to subsequent reports. */
    fun setCustomKey(key: String, value: Any?)

    /** Associates subsequent reports with a user identity. */
    fun setUserId(userId: String?)
}
