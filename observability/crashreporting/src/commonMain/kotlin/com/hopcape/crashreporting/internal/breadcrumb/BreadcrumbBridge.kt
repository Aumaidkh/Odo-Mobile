package com.hopcape.crashreporting.internal.breadcrumb

import kotlin.concurrent.Volatile

// ─────────────────────────────────────────────────────────────
// BreadcrumbBridge — a single process-wide BreadcrumbTrail that both
// the crash reporter and (eventually) the Logger write into, so one
// log call doubles as a crash breadcrumb without call sites doing
// bookkeeping twice.
//
// It is an object (not injected) because the trail must be reachable
// from a fatal handler running as the process dies — that path can't
// resolve a DI graph. CrashReporter.leaveBreadcrumb() forwards here;
// the CrashReporterImpl reads snapshot() when building a report.
//
// FUTURE (out of scope for this slice): tap FFLogger so an
// `FFLogger.tag("AUTH").i("login_started")` call also lands here at
// INFO+ level. Doing that requires :observability:logging to call
// into this bridge — a deliberate cross-module edge left for a
// follow-up, to keep this module self-contained.
// ─────────────────────────────────────────────────────────────
internal object BreadcrumbBridge {

    @Volatile
    private var trail: BreadcrumbTrail = BreadcrumbTrail()

    /** Current trail. Rebound by [configure] at init so [capture] and reports share one buffer. */
    fun trail(): BreadcrumbTrail = trail

    /** Installs the trail built from CrashConfig (honours the configured breadcrumb limit). */
    fun configure(newTrail: BreadcrumbTrail) {
        trail = newTrail
    }

    /** Records a breadcrumb into the shared trail. The one entry point log bridges call. */
    fun capture(tag: String, message: String) = trail.add(tag, message)
}
