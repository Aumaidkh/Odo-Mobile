package com.hopcape.performance.internal

import com.hopcape.performance.api.PerformanceConfig
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.internal.dispatch.BatchSpanDispatcher
import com.hopcape.performance.internal.export.ConsoleSpanExporter
import com.hopcape.performance.internal.export.SafeSpanExporter
import com.hopcape.performance.internal.export.SinkSpanExporter
import com.hopcape.performance.internal.export.SpanExporter
import com.hopcape.performance.internal.model.SpanContext
import com.hopcape.performance.internal.sampling.AdaptiveSampler
import com.hopcape.performance.internal.store.InMemorySpanStore

// ─────────────────────────────────────────────────────────────
// PerformanceFactory — Factory + composition root for the tracer,
// mirroring the analytics AnalyticsFactory. This is the ONE place
// that names concrete types (the sink adapter, the console exporter,
// the in-memory store, the adaptive sampler, the dispatcher);
// everything else depends only on interfaces (DIP). It is `internal`:
// the public entry points are the APM facade and the performanceModule
// Koin binding, both of which feed a PerformanceConfig through here.
// ─────────────────────────────────────────────────────────────
internal object PerformanceFactory {

    /**
     * Builds a fully-wired [PerformanceTracer] from [config]. [contextProvider]
     * supplies the live [SpanContext] (owned by the facade) so session updates
     * land on every subsequent span without re-wiring.
     */
    fun create(config: PerformanceConfig, contextProvider: () -> SpanContext): PerformanceTracer {
        val rawExporters = buildList {
            addAll(config.destinations.map(::SinkSpanExporter))   // vendor backends (e.g. Firebase)
            if (config.isDebug) add(ConsoleSpanExporter())
        }
        // Every exporter is exception-isolated so one failing backend can never
        // crash the app; SafeSpanExporter re-throws so the dispatcher still sees
        // the failure and can retry / dead-letter.
        val exporters: List<SpanExporter> = rawExporters.map { exporter ->
            SafeSpanExporter(exporter) { name, error ->
                config.onDiagnostic("exporter '$name' failed: ${error.message}")
            }
        }

        val store = InMemorySpanStore()
        val dispatcher = BatchSpanDispatcher(
            store = store,
            exporters = exporters,
            batchSize = config.batchSize,
            flushInterval = config.flushInterval,
            onDropped = { span, error ->
                config.onDiagnostic("dropped span '${span.name}' after retries: ${error.message}")
            },
        ).also { it.start() }

        val sampler = AdaptiveSampler(
            sampleRate = config.sampleRate,
            slowThresholdMs = config.slowSpanThreshold.inWholeMilliseconds,
            keepAll = config.isDebug,
        )

        return PerformanceTracerImpl(
            store = store,
            sampler = sampler,
            dispatcher = dispatcher,
            contextProvider = contextProvider,
        )
    }
}
