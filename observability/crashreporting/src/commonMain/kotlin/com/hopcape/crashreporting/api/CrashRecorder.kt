package com.hopcape.crashreporting.api

// ─────────────────────────────────────────────────────────────
// CrashRecorder — the minimal public contract (ISP). This is what
// ViewModels / repositories / use cases depend on and inject. They
// never see destinations, the file store, breadcrumb buffer, or the
// PII redactor — all of that is `internal` to the module.
//
// Note there is no `recordFatal` here: fatal (uncaught) crashes are
// captured automatically by the installed handler, not by call
// sites. Call sites only ever report *handled* exceptions
// (recordNonFatal) and enrich reports (breadcrumbs / custom keys).
// ─────────────────────────────────────────────────────────────
interface CrashRecorder {

    /**
     * Records a handled (non-fatal) exception — one the app recovered from but
     * that should still be visible on the crash dashboard. [customKeys] attach
     * arbitrary context (values are PII-scrubbed before delivery). Safe to call
     * from any thread; delivered immediately since the app is alive.
     */
    fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?> = emptyMap())

    /**
     * Leaves a breadcrumb — a lightweight trace of what happened just before a
     * crash. Kept in a bounded in-memory ring buffer and attached to the next
     * report. [tag] groups related breadcrumbs (e.g. "AUTH", "BLE").
     */
    fun leaveBreadcrumb(tag: String, message: String)

    /** Sets a custom key attached to subsequent reports and forwarded to every destination. */
    fun setCustomKey(key: String, value: Any?)

    /** Associates subsequent reports with a user identity (forwarded to every destination). */
    fun setUserId(userId: String?)
}
