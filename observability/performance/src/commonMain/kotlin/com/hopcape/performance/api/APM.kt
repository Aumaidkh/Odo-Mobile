@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.performance.api

import com.hopcape.performance.internal.PerformanceFactory
import com.hopcape.performance.internal.model.SpanContext
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

// ─────────────────────────────────────────────────────────────
// APM — the Facade (Application Performance Monitoring). Mirrors
// HLogger / HAnalytics deliberately so the three observability
// systems feel like one API family. This is the ONE composition
// call and the entry point most call sites use.
//
// Design choices (same rationale as HLogger / HAnalytics):
// - init() must be called once (Application.onCreate). Enforced via
//   an AtomicBoolean CAS — a duplicate call is a no-op + diagnostic,
//   never a crash or a re-wire (fail-safe applied to the API itself).
// - Before init() the delegate is a no-op tracer that hands back
//   inert spans — a misordered call records nothing instead of
//   throwing in production. (So: call APM.init() BEFORE the first
//   startSpan you actually want captured — e.g. the cold-start span.)
// - The facade owns the mutable SpanContext (session/app metadata);
//   the delegate reads it live through a provider, so setSession()
//   updates land on every subsequent span without re-wiring.
// ─────────────────────────────────────────────────────────────
object APM {

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var delegate: PerformanceTracer = NoOpTracer

    @Volatile
    private var spanContext = SpanContext(
        appVersion = "", deviceModel = "", osVersion = "", locale = "",
    )

    /**
     * Must be called exactly once, typically from `Application.onCreate()`.
     * Safe to call again (idempotent no-op + diagnostic) — it never throws, so a
     * duplicate init in test setup or a multi-process app can't crash the host.
     */
    @JvmStatic
    fun init(config: PerformanceConfig) {
        if (!initialized.compareAndSet(false, true)) {
            config.onDiagnostic("APM already initialized — ignoring duplicate init()")
            return
        }
        spanContext = SpanContext(
            appVersion = config.appVersion,
            deviceModel = config.deviceModel,
            osVersion = config.osVersion,
            locale = config.locale,
        )
        delegate = PerformanceFactory.create(config) { spanContext }
    }

    /**
     * Convenience overload for call sites that only have a debug flag on hand
     * (`APM.init(isDebug = BuildConfig.DEBUG)`). Device/app metadata is left blank
     * and sensible defaults apply; prefer the [PerformanceConfig] overload when
     * you can supply full context.
     */
    @JvmStatic
    fun init(isDebug: Boolean) = init(
        PerformanceConfig(appVersion = "", deviceModel = "", osVersion = "", locale = "", isDebug = isDebug),
    )

    /** Sets the process-wide session id (e.g. per app-open), stamped onto subsequent spans. */
    @JvmStatic
    fun setSession(sessionId: String) {
        spanContext = spanContext.copy(sessionId = sessionId)
    }

    /**
     * Opens a span named [name] on trace [traceId]. Pass [parentSpanId] to nest it
     * under a parent span within the same trace. Chain [Span.setAttribute] on the
     * result and close it with [endSpan]. Before [init] this returns an inert span.
     */
    @JvmStatic
    @JvmOverloads
    fun startSpan(name: String, traceId: String, parentSpanId: String? = null): Span =
        delegate.startSpan(name, traceId, parentSpanId)

    /** Closes [span] and records it (subject to sampling). Safe to call before [init]. */
    @JvmStatic
    fun endSpan(span: Span) = delegate.endSpan(span)

    @JvmStatic
    fun flush() = delegate.flush()

    /**
     * Exposes the facade's underlying [PerformanceTracer] for DI graphs, so a
     * `koinInject<PerformanceTracer>()` and a static `APM.startSpan(...)` call
     * share ONE configuration and ONE export pipeline. This is what
     * [performanceModule] binds.
     *
     * Reflects [init] live: before init() it routes to the no-op fallback; after,
     * to the configured pipeline. Injectors therefore never need to sequence
     * against init().
     */
    @JvmStatic
    fun asTracer(): PerformanceTracer = DelegatingTracer

    /** Stable reference for DI that always forwards to the live [delegate]. */
    private object DelegatingTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            delegate.startSpan(name, traceId, parentSpanId)

        override fun endSpan(span: Span) = delegate.endSpan(span)
        override fun flush() = delegate.flush()
    }

    /** Pre-init fallback — satisfies the contract (LSP) while recording nothing. */
    private object NoOpTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            NoOpSpan(name, traceId, parentSpanId)

        override fun endSpan(span: Span) {}
        override fun flush() {}
    }

    /** Inert span handed out before init — accepts calls, records nothing. */
    private class NoOpSpan(
        override val name: String,
        override val traceId: String,
        override val parentSpanId: String?,
    ) : Span {
        override val spanId: String = "noop"
        override fun setAttribute(key: String, value: Any?): Span = this
    }
}

/**
 * Brandable, ergonomic alias for the [APM] facade. Both names refer to the same
 * object, so there is no second entry point — pick whichever reads better at the
 * call site.
 */
typealias Perf = APM
