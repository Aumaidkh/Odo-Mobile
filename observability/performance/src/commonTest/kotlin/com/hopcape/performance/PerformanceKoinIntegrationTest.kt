package com.hopcape.performance

import com.hopcape.performance.api.APM
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.performanceModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Integration test for the DI wiring: `performanceModule` must republish the
 * facade's single tracer, so `koinInject<PerformanceTracer>()` and static
 * `APM.startSpan(...)` resolve to the same underlying pipeline — one config, one
 * dispatch queue, one set of exporters.
 *
 * These tests deliberately never call [APM.init]: the facade is a process-wide
 * singleton, so they only assert the DI binding and pre-init fail-safe behaviour.
 */
class PerformanceKoinIntegrationTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun bindsTracerAsSingleton() {
        val koin = startKoin { modules(performanceModule) }.koin
        assertSame(koin.get<PerformanceTracer>(), koin.get<PerformanceTracer>(), "tracer must be a single")
    }

    @Test
    fun injectedTracer_isTheFacadesTracer() {
        val koin = startKoin { modules(performanceModule) }.koin
        assertSame(
            APM.asTracer(),
            koin.get<PerformanceTracer>(),
            "performanceModule must republish APM.asTracer(), not build its own tracer",
        )
    }

    @Test
    fun injectedTracer_isUsableBeforeInit_withoutThrowing() {
        val koin = startKoin { modules(performanceModule) }.koin
        val tracer = koin.get<PerformanceTracer>()

        // Before APM.init this routes to the no-op fallback — must hand back an inert
        // span and swallow endSpan, never throw.
        val span = tracer.startSpan("op", traceId = "t").setAttribute("k", "v")
        assertTrue(span.spanId.isNotEmpty())
        tracer.endSpan(span)
        tracer.flush()
    }
}
