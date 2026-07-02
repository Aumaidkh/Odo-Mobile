package com.hopcape.crashreporting

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.crashreporting.api.CrashReporter
import com.hopcape.crashreporting.api.crashReportingModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Integration test for the DI wiring: `crashReportingModule` must republish the
 * facade's single recorder, so `koinInject<CrashRecorder>()` and static
 * `CrashReporter.recordNonFatal(...)` resolve to the same underlying pipeline.
 *
 * These tests deliberately never call [CrashReporter.init]: the facade is a
 * process-wide singleton (and init installs a global uncaught handler), so they
 * only assert the DI binding and pre-init fail-safe behaviour.
 */
class CrashReportingKoinIntegrationTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun bindsRecorderAsSingleton() {
        val koin = startKoin { modules(crashReportingModule) }.koin
        assertSame(koin.get<CrashRecorder>(), koin.get<CrashRecorder>(), "recorder must be a single")
    }

    @Test
    fun injectedRecorder_isTheFacadesRecorder() {
        val koin = startKoin { modules(crashReportingModule) }.koin
        assertSame(
            CrashReporter.asRecorder(),
            koin.get<CrashRecorder>(),
            "crashReportingModule must republish CrashReporter.asRecorder(), not build its own",
        )
    }

    @Test
    fun injectedRecorder_isUsableBeforeInit_withoutThrowing() {
        val koin = startKoin { modules(crashReportingModule) }.koin
        val recorder = koin.get<CrashRecorder>()

        // Before CrashReporter.init this routes to the no-op fallback — must
        // swallow record/keys and still accept breadcrumbs, never throw.
        recorder.leaveBreadcrumb("BOOT", "pre-init crumb")
        recorder.setCustomKey("k", "v")
        recorder.setUserId("u")
        recorder.recordNonFatal(RuntimeException("pre-init"))
        assertTrue(true)
    }
}
