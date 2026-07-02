@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.logging.api

import com.hopcape.logging.internal.LoggerFactory
import com.hopcape.logging.internal.utils.SafeMap
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

// ─────────────────────────────────────────────────────────────
// HLogger — the Facade. This is the ONLY class most engineers
// ever import directly.
//
// Design choices explained:
// - `init()` must be called once (Application.onCreate). Enforced
//   via AtomicBoolean guard — calling twice is a no-op + warning,
//   not a crash (fail-safe principle applied to the API itself).
// - Falls back to a no-op logger if used before init() — so a
//   misordered call in some obscure code path logs nothing
//   instead of throwing NPE in production.
// - `runtimeMinLevel` map allows remote-config-driven verbosity
//   changes per tag without an app update (e.g. turn on VERBOSE
//   for "BLE_GATT" for one user session while debugging a field issue).
// ─────────────────────────────────────────────────────────────
@StableLoggerApi
object HLogger {

    private val initialized = AtomicBoolean(false)
    private val runtimeMinLevel = SafeMap<String, LogLevel>()

    @Volatile
    private var delegate: Logger = LoggerFactory.createNoOpLogger()

    @Volatile
    private var globalTraceContext: TraceContext = TraceContext()

    /**
     * Must be called exactly once, typically from `Application.onCreate()`.
     * Safe to call again (idempotent no-op + internal warning) — will
     * never throw, so a duplicate init in test setup or multi-process
     * apps can't crash the host.
     */
    @JvmStatic
    fun init(config: LoggerConfig) {
        if (!initialized.compareAndSet(false, true)) {
            internalLog("HLogger already initialized — ignoring duplicate init()")
            return
        }
        delegate = LoggerFactory.create(config)
    }

    /** Sets the process-wide session identity. Call once per app session (e.g. post-login). */
    @JvmStatic
    fun setSession(sessionId: String) {
        globalTraceContext = globalTraceContext.copy(sessionId = sessionId)
    }

    /**
     * Runtime override for a specific tag's minimum level — e.g. driven by
     * a remote-config flag to enable VERBOSE logging for "BLE_GATT" only
     * for a specific debugging session, without shipping a new build.
     */
    @JvmStatic
    fun setTagLevelOverride(tag: String, level: LogLevel) {
        runtimeMinLevel[tag] = level
    }

    @JvmStatic
    fun clearTagLevelOverride(tag: String) {
        runtimeMinLevel.remove(tag)
    }

    /** Returns a [ScopedLogger] bound to [tag], inheriting the current session context. */
    @JvmStatic
    @JvmOverloads
    fun tag(tag: String, flowId: String? = null): ScopedLogger {
        val ctx = flowId?.let { globalTraceContext.withNewFlow(it) } ?: globalTraceContext
        return ScopedLogger(EffectiveLogger, tag, ctx)
    }

    @JvmStatic
    fun flush() = delegate.flush()

    /**
     * Exposes the facade's underlying [Logger] (tag-override aware) for DI graphs,
     * so a `koinInject<Logger>()` and a static `HLogger.tag(...)` call share ONE
     * configuration and ONE set of sinks. This is what [loggingModule] binds.
     *
     * Reflects [init] live: before init() it routes to the no-op fallback; after,
     * to the configured sinks. Injectors therefore never need to sequence against
     * init() — they resolve a stable reference and see the real logger once ready.
     */
    @JvmStatic
    fun asLogger(): Logger = EffectiveLogger

    /**
     * Internal router that applies runtime tag-level overrides before
     * delegating to the configured sinks. Kept private — external
     * callers only ever see [ScopedLogger] / [tag].
     */
    private object EffectiveLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>
        ) {
            val effectiveMin = runtimeMinLevel[tag]
            if (effectiveMin != null && level.priority < effectiveMin.priority) return
            delegate.log(level, tag, event, traceContext, fields)
        }

        override fun flush() = delegate.flush()
    }

    // Diagnostics for the facade's own lifecycle (duplicate init, etc.). Kept
    // dependency-free; swap for a platform log via expect/actual if needed.
    private fun internalLog(message: String) {
        println("[HLogger-internal] $message")
    }
}
