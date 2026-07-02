@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.crashreporting.internal

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.crashreporting.api.DeviceContext
import com.hopcape.crashreporting.internal.breadcrumb.BreadcrumbTrail
import com.hopcape.crashreporting.internal.destinations.CrashDestination
import com.hopcape.crashreporting.internal.model.CrashReport
import com.hopcape.crashreporting.internal.platform.installUncaughtCrashHandler
import com.hopcape.crashreporting.internal.store.CrashFileStore
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// ─────────────────────────────────────────────────────────────
// CrashReporterImpl — orchestrates the crash lifecycle. Each concern
// lives in a collaborator (SRP); this class only sequences them and
// depends solely on abstractions (DIP). Assembled once by
// CrashReporterFactory, which also calls install().
//
// Two intentionally different paths:
//  • FATAL (uncaught): the process is dying, so we ONLY write the
//    report to disk, SYNCHRONOUSLY. No network — an async call
//    wouldn't finish. It's uploaded on the next launch.
//  • NON-FATAL (handled): the app is alive, so we deliver to the
//    destinations immediately; nothing is persisted.
// ─────────────────────────────────────────────────────────────
internal class CrashReporterImpl(
    private val destinations: List<CrashDestination>,
    private val fileStore: CrashFileStore,
    private val breadcrumbs: BreadcrumbTrail,
    private val deviceContextProvider: () -> DeviceContext,
    private val onDiagnostic: (String) -> Unit,
    // Registers the fatal-crash callback with the platform. Injected (defaulting
    // to the real expect/actual seam) so tests can capture and fire the callback
    // without touching the process-global uncaught handler.
    private val installHandler: ((Throwable) -> Unit) -> Unit = ::installUncaughtCrashHandler,
) : CrashRecorder {

    // Accumulated custom keys, applied to every subsequent report. Copy-on-write
    // so the fatal handler can read a consistent snapshot without locking.
    private val customKeys = AtomicReference<Map<String, Any?>>(emptyMap())

    /**
     * Installs the uncaught handler (fatal → writeSync) and immediately uploads
     * any reports persisted before a previous process died. Called once by the
     * factory, mirroring how the APM factory calls dispatcher.start().
     */
    fun install() {
        installHandler { throwable -> handleFatal(throwable) }
        uploadPending()
    }

    // ── CrashRecorder ───────────────────────────────────────

    override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
        val report = buildReport(throwable, isFatal = false, extraKeys = customKeys)
        // App is alive — safe to deliver now. Each destination is already
        // Safe(Redacting(...)), so a throwing vendor can't abort the others.
        destinations.forEach { it.record(report) }
    }

    override fun leaveBreadcrumb(tag: String, message: String) = breadcrumbs.add(tag, message)

    override fun setCustomKey(key: String, value: Any?) {
        mutateKeys { it + (key to value) }
        destinations.forEach { it.setCustomKey(key, value) }
    }

    override fun setUserId(userId: String?) {
        destinations.forEach { it.setUserId(userId) }
    }

    // ── Fatal path ──────────────────────────────────────────

    private fun handleFatal(throwable: Throwable) {
        // Deliberately the ONLY thing done here: a synchronous disk write. The
        // process is about to die; the report is uploaded on next launch.
        val report = buildReport(throwable, isFatal = true, extraKeys = emptyMap())
        fileStore.writeSync(report)
    }

    private fun uploadPending() {
        fileStore.readPending().forEach { report ->
            destinations.forEach { it.record(report) }
            fileStore.clear(report.crashId)
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun buildReport(throwable: Throwable, isFatal: Boolean, extraKeys: Map<String, Any?>): CrashReport =
        CrashReport.from(
            throwable = throwable,
            isFatal = isFatal,
            breadcrumbs = breadcrumbs.snapshot(),
            customKeys = customKeys.load() + extraKeys, // call-site keys win over accumulated
            deviceContext = runCatching(deviceContextProvider).getOrElse {
                onDiagnostic("deviceContextProvider threw: ${it.message}")
                DeviceContext(appVersion = "", osVersion = "", deviceModel = "")
            },
        )

    private inline fun mutateKeys(transform: (Map<String, Any?>) -> Map<String, Any?>) {
        while (true) {
            val current = customKeys.load()
            if (customKeys.compareAndSet(current, transform(current))) return
        }
    }
}
