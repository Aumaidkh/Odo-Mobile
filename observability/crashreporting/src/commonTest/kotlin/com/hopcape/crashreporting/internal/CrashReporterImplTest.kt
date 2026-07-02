package com.hopcape.crashreporting.internal

import com.hopcape.crashreporting.RecordingCrashDestination
import com.hopcape.crashreporting.RecordingCrashFileStore
import com.hopcape.crashreporting.testDeviceContext
import com.hopcape.crashreporting.internal.breadcrumb.BreadcrumbTrail
import com.hopcape.crashreporting.internal.destinations.CrashDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashReporterImplTest {

    private fun impl(
        destinations: List<CrashDestination>,
        fileStore: RecordingCrashFileStore = RecordingCrashFileStore(),
        breadcrumbs: BreadcrumbTrail = BreadcrumbTrail(),
        captureFatal: (((Throwable) -> Unit) -> Unit) = {},
    ) = CrashReporterImpl(
        destinations = destinations,
        fileStore = fileStore,
        breadcrumbs = breadcrumbs,
        deviceContextProvider = { testDeviceContext() },
        onDiagnostic = {},
        installHandler = captureFatal,
    )

    @Test
    fun nonFatal_isDeliveredToDestinationsImmediately_notPersisted() {
        val dest = RecordingCrashDestination()
        val store = RecordingCrashFileStore()
        val reporter = impl(listOf(dest), store)

        reporter.recordNonFatal(IllegalStateException("boom"), mapOf("k" to "v"))

        assertEquals(1, dest.recorded.size)
        val report = dest.recorded.single()
        assertEquals("IllegalStateException", report.throwableType)
        assertEquals(false, report.isFatal)
        assertEquals("v", report.customKeys["k"])
        assertTrue(store.written.isEmpty(), "non-fatal must not be persisted — the app is alive")
    }

    @Test
    fun fatal_isWrittenSynchronously_notSentToDestinations() {
        val dest = RecordingCrashDestination()
        val store = RecordingCrashFileStore()
        var fire: ((Throwable) -> Unit)? = null
        val reporter = impl(listOf(dest), store, captureFatal = { fire = it })

        reporter.install()
        // Simulate the platform delivering an uncaught exception.
        fire!!.invoke(RuntimeException("fatal boom"))

        assertEquals(1, store.written.size, "fatal must be persisted synchronously")
        assertTrue(store.written.values.single().isFatal)
        assertTrue(dest.recorded.isEmpty(), "no network delivery on the dying-process path")
    }

    @Test
    fun install_uploadsPendingReportsFromPreviousRun_thenClearsThem() {
        val dest = RecordingCrashDestination()
        val store = RecordingCrashFileStore()
        // A crash persisted by a previous process that never got to upload it.
        store.writeSync(com.hopcape.crashreporting.testReport(crashId = "old", isFatal = true))
        val reporter = impl(listOf(dest), store)

        reporter.install()

        assertEquals(listOf("old"), dest.recorded.map { it.crashId }, "pending crash uploaded on init")
        assertEquals(listOf("old"), store.cleared, "and cleared after successful upload")
    }

    @Test
    fun recordNonFatal_includesBreadcrumbsAndAccumulatedCustomKeys() {
        val dest = RecordingCrashDestination()
        val trail = BreadcrumbTrail()
        val reporter = impl(listOf(dest), breadcrumbs = trail)

        reporter.leaveBreadcrumb("AUTH", "login_started")
        reporter.setCustomKey("build", "debug")
        reporter.recordNonFatal(RuntimeException("x"), mapOf("build" to "override"))

        val report = dest.recorded.single()
        assertEquals("login_started", report.breadcrumbs.single().message)
        assertEquals("override", report.customKeys["build"], "call-site keys win over accumulated")
    }

    @Test
    fun aThrowingDestination_doesNotAbortTheOthers() {
        val boom = RecordingCrashDestination(name = "boom", failRecordTimes = 1)
        val healthy = RecordingCrashDestination(name = "healthy")
        // Wrap the throwing one as the factory would (Safe) so record() is isolated.
        val safeBoom = com.hopcape.crashreporting.internal.destinations.SafeCrashDestination(boom) { _, _ -> }
        val reporter = impl(listOf(safeBoom, healthy))

        reporter.recordNonFatal(RuntimeException("x"))

        assertEquals(1, healthy.recorded.size, "healthy destination still receives the report")
    }

    @Test
    fun setCustomKeyAndUserId_areForwardedToDestinations() {
        val dest = RecordingCrashDestination()
        val reporter = impl(listOf(dest))

        reporter.setCustomKey("screen", "home")
        reporter.setUserId("u-9")

        assertEquals("screen" to "home", dest.customKeys.single())
        assertEquals("u-9", dest.userId)
    }
}
