package com.hopcape.performance.api

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DI graph for performance monitoring — the module's Koin entry point alongside
 * the [APM] facade. Rather than building its own tracer, it **republishes** the
 * facade's underlying [PerformanceTracer] ([APM.asTracer]) into the graph. So DI
 * consumers (`koinInject<PerformanceTracer>()` / constructor injection) and static
 * `APM.startSpan(...)` calls resolve to ONE configured tracer — one config, one
 * dispatch queue, one set of exporters.
 *
 * Configuration therefore lives in exactly one place: the app's single [APM.init]
 * call at startup. Callers only ever see the [PerformanceTracer] interface; every
 * sampler / dispatcher / exporter stays `internal` to this module.
 */
val performanceModule: Module = module {
    single<PerformanceTracer> { APM.asTracer() }
}
