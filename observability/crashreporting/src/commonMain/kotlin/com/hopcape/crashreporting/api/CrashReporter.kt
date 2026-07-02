@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.crashreporting.api

import com.hopcape.crashreporting.internal.CrashReporterFactory
import com.hopcape.crashreporting.internal.breadcrumb.BreadcrumbBridge
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

// ─────────────────────────────────────────────────────────────
// CrashReporter — the Facade. Mirrors HLogger / HAnalytics / APM
// deliberately so the four observability systems feel like one API
// family. This is the ONE composition call and the entry point most
// call sites use.
//
// Design choices (same rationale as the siblings):
// - init() must be called once (Application.onCreate), BEFORE the
//   first exception you want captured. Enforced via an AtomicBoolean
//   CAS — a duplicate call is a no-op + diagnostic, never a crash or
//   a re-wire (fail-safe applied to the API itself). init() is also
//   where the uncaught-exception handler gets installed and any
//   crash from a previous run gets uploaded.
// - Before init() the delegate is a no-op recorder — a misordered
//   recordNonFatal drops silently instead of throwing. leaveBreadcrumb
//   still works pre-init (it routes to the shared trail), so early
//   crumbs aren't lost.
// ─────────────────────────────────────────────────────────────
object CrashReporter {

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var delegate: CrashRecorder = NoOpReporter

    /**
     * Must be called exactly once, typically from `Application.onCreate()`, as
     * early as possible so the uncaught-exception handler is installed before any
     * crash. Safe to call again (idempotent no-op + diagnostic) — it never throws,
     * so a duplicate init in test setup or a multi-process app can't crash the host.
     */
    @JvmStatic
    fun init(config: CrashConfig) {
        if (!initialized.compareAndSet(false, true)) {
            config.onDiagnostic("CrashReporter already initialized — ignoring duplicate init()")
            return
        }
        delegate = CrashReporterFactory.create(config)
    }

    /** Records a handled (non-fatal) exception. See [CrashRecorder.recordNonFatal]. */
    @JvmStatic
    @JvmOverloads
    fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?> = emptyMap()) =
        delegate.recordNonFatal(throwable, customKeys)

    /** Leaves a breadcrumb. Works before [init] too (routes to the shared trail). */
    @JvmStatic
    fun leaveBreadcrumb(tag: String, message: String) = delegate.leaveBreadcrumb(tag, message)

    /** Sets a custom key attached to subsequent reports. */
    @JvmStatic
    fun setCustomKey(key: String, value: Any?) = delegate.setCustomKey(key, value)

    /** Associates subsequent reports with a user identity. */
    @JvmStatic
    fun setUserId(userId: String?) = delegate.setUserId(userId)

    /**
     * Exposes the facade's underlying [CrashRecorder] for DI graphs, so a
     * `koinInject<CrashRecorder>()` and a static `CrashReporter.recordNonFatal(...)`
     * call share ONE configuration and ONE set of destinations. This is what
     * [crashReportingModule] binds.
     *
     * Reflects [init] live: before init() it routes to the no-op fallback; after,
     * to the configured pipeline. Injectors therefore never need to sequence
     * against init().
     */
    @JvmStatic
    fun asRecorder(): CrashRecorder = DelegatingRecorder

    /** Stable reference for DI that always forwards to the live [delegate]. */
    private object DelegatingRecorder : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) =
            delegate.recordNonFatal(throwable, customKeys)

        override fun leaveBreadcrumb(tag: String, message: String) = delegate.leaveBreadcrumb(tag, message)
        override fun setCustomKey(key: String, value: Any?) = delegate.setCustomKey(key, value)
        override fun setUserId(userId: String?) = delegate.setUserId(userId)
    }

    /**
     * Pre-init fallback — satisfies the contract (LSP) while dropping delivery.
     * Breadcrumbs are the exception: they route to the shared trail so crumbs
     * left before init() survive into the first report.
     */
    private object NoOpReporter : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {}
        override fun leaveBreadcrumb(tag: String, message: String) = BreadcrumbBridge.capture(tag, message)
        override fun setCustomKey(key: String, value: Any?) {}
        override fun setUserId(userId: String?) {}
    }
}

/**
 * Brandable, ergonomic alias for the [CrashReporter] facade. Both names refer to
 * the same object, so there is no second entry point — pick whichever reads
 * better at the call site (`Crash.recordNonFatal(e)`).
 */
typealias Crash = CrashReporter
