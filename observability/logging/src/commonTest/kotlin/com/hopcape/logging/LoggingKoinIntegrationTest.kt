package com.hopcape.logging

import com.hopcape.logging.api.HLogger
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.loggingModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Integration test for the DI wiring: `loggingModule` must republish the facade's
 * single logger, so `koinInject<Logger>()` and `HLogger.tag(...)` resolve to the
 * same underlying pipeline — no duplicate logger/file writers.
 */
class LoggingKoinIntegrationTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun bindsLoggerAsSingleton() {
        val koin = startKoin { modules(loggingModule) }.koin

        assertSame(koin.get<Logger>(), koin.get<Logger>(), "Logger must be a single")
    }

    @Test
    fun injectedLogger_isTheFacadesLogger() {
        val koin = startKoin { modules(loggingModule) }.koin

        assertSame(
            HLogger.asLogger(),
            koin.get<Logger>(),
            "loggingModule must republish HLogger.asLogger(), not build its own logger",
        )
    }

    @Test
    fun injectedLogger_isUsableWithoutThrowing() {
        val koin = startKoin { modules(loggingModule) }.koin

        // Before HLogger.init this routes to the no-op fallback — must not throw.
        koin.get<Logger>().info("Koin", "wired")
    }
}
