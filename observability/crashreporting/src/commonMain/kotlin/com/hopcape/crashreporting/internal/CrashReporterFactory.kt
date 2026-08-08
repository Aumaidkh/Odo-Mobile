package com.hopcape.crashreporting.internal

import com.hopcape.crashreporting.api.CrashConfig
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.crashreporting.internal.breadcrumb.BreadcrumbBridge
import com.hopcape.crashreporting.internal.breadcrumb.BreadcrumbTrail
import com.hopcape.crashreporting.internal.destinations.CrashDestination
import com.hopcape.crashreporting.internal.destinations.ConsoleCrashDestination
import com.hopcape.crashreporting.internal.destinations.RedactingCrashDestination
import com.hopcape.crashreporting.internal.destinations.SafeCrashDestination
import com.hopcape.crashreporting.internal.destinations.SentryDestination
import com.hopcape.crashreporting.internal.destinations.SinkCrashDestination
import com.hopcape.crashreporting.internal.platform.createCrashFileStore
import com.hopcape.crashreporting.internal.redactor.RegexCrashPiiRedactor

// ─────────────────────────────────────────────────────────────
// CrashReporterFactory — Factory + composition root, mirroring the
// APM PerformanceFactory. The ONE place that names concrete built-in
// types (the redactor, the platform file store); everything else
// depends only on interfaces (DIP). Vendor destinations this module
// doesn't own (e.g. Firebase Crashlytics) arrive via
// CrashConfig.destinations as CrashSink and are adapted with
// SinkCrashDestination — the analog of the analytics module's
// AnalyticsFactory / SinkDestination. Internal: the public entry
// points are the CrashReporter facade and the crashReportingModule
// Koin binding, both of which feed a CrashConfig through here.
// ─────────────────────────────────────────────────────────────
internal object CrashReporterFactory {

    /** Builds and installs a fully-wired [CrashRecorder] from [config]. */
    fun create(config: CrashConfig): CrashRecorder {
        // Publish the configured trail so BreadcrumbBridge.capture (and any future
        // logger bridge) writes into the same buffer the reporter reads.
        val trail = BreadcrumbTrail(config.breadcrumbLimit)
        BreadcrumbBridge.configure(trail)

        val redactor = RegexCrashPiiRedactor()
        val rawDestinations = buildList {
            config.destinations.forEach { add(SinkCrashDestination(it)) } // e.g. Firebase Crashlytics
            config.sentryDsn?.let { add(SentryDestination(it)) }
            if (config.isDebug) add(ConsoleCrashDestination())
        }
        // Compose Safe(Redacting(vendor)): every report is PII-scrubbed before
        // delivery, and a throwing vendor (or redactor) can never crash the host
        // or abort delivery to the other destinations.
        val destinations: List<CrashDestination> = rawDestinations.map { destination ->
            SafeCrashDestination(RedactingCrashDestination(destination, redactor)) { name, error ->
                config.onDiagnostic("destination '$name' failed: ${error.message}")
            }
        }

        val fileStore = createCrashFileStore(config.crashDirPath)

        return CrashReporterImpl(
            destinations = destinations,
            fileStore = fileStore,
            breadcrumbs = trail,
            deviceContextProvider = config.deviceContextProvider,
            onDiagnostic = config.onDiagnostic,
        ).also { it.install() } // install the uncaught handler + upload prior-run crashes now
    }
}
