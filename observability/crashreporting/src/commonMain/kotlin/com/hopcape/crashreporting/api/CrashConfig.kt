package com.hopcape.crashreporting.api

// ─────────────────────────────────────────────────────────────
// CrashConfig — immutable configuration consumed by
// CrashReporter.init(). Mirrors PerformanceConfig / AnalyticsConfig:
// one value object, built once at startup, that fully describes the
// reporter. The app supplies app/device metadata and the storage
// location; the module owns capture, redaction, persistence, and
// delivery.
// ─────────────────────────────────────────────────────────────
data class CrashConfig(
    /** App version string stamped onto every report's device context. */
    val appVersion: String,

    /** OS version string (e.g. "Android 14") stamped onto every report. */
    val osVersion: String,

    /** Device model (e.g. "Pixel 8") stamped onto every report. */
    val deviceModel: String,

    /**
     * Absolute directory path where fatal crashes are written **synchronously**
     * before the process dies (see DiskCrashFileStore). Supplied by the app
     * (e.g. `context.filesDir/crash`). When blank, an in-memory store is used —
     * fine for tests, but fatal reports won't survive a real process death.
     */
    val crashDirPath: String = "",

    /** Sentry DSN; when non-null a Sentry destination is added alongside Crashlytics. */
    val sentryDsn: String? = null,

    /** Debug builds additionally print each report to the console. */
    val isDebug: Boolean = false,

    /** Max breadcrumbs retained in the ring buffer; oldest are dropped past this. */
    val breadcrumbLimit: Int = DEFAULT_BREADCRUMB_LIMIT,

    /**
     * Supplies the live [DeviceContext] at the moment of a crash (lazily invoked),
     * so dynamic fields (free memory, battery, network) reflect the pre-crash
     * state rather than startup. Defaults to a static context built from the
     * fields above with the dynamic fields left unknown.
     */
    val deviceContextProvider: () -> DeviceContext = {
        DeviceContext(appVersion = appVersion, osVersion = osVersion, deviceModel = deviceModel)
    },

    /**
     * Diagnostics channel for the module's own lifecycle (duplicate init, a
     * destination failing, a report that couldn't be written). Defaults to a
     * no-op; wire it to your logger in debug builds. Kept as a `String` sink so
     * no internal type leaks across the public boundary.
     */
    val onDiagnostic: (String) -> Unit = {},
) {
    companion object {
        const val DEFAULT_BREADCRUMB_LIMIT: Int = 50
    }
}
