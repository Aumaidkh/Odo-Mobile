package com.hopcape.odo

import android.app.Application
import android.os.Build
import android.util.Log
import com.hopcape.analytics.api.AnalyticsConfig
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.HAnalytics
import com.hopcape.crashreporting.api.CrashConfig
import com.hopcape.crashreporting.api.CrashReporter
import com.hopcape.logging.api.HLogger
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.LoggerConfig
import com.hopcape.logging.api.loggerConfig
import com.hopcape.odo.core.data.db.DriverFactory
import com.hopcape.odo.feature.documentvault.documentVaultAndroidModule
import com.hopcape.odo.di.initKoin
import com.hopcape.odo.di.odoAnalyticsEvents
import com.hopcape.performance.api.APM
import com.hopcape.performance.api.PerformanceConfig
import com.hopcape.performance.api.Span
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Android entry point: starts the Koin graph before the first Activity. It supplies
 * the two things only the platform can — the application [android.content.Context]
 * (via `androidContext()`) and the Context-bearing SQLDelight [DriverFactory] — and
 * lets the shared [initKoin] wire the rest (navigation, data layer, features).
 *
 * It also opens the **cold-start** span here and closes it from [MainActivity] once
 * the first frame is drawn (see [coldStartSpan] / [appSessionId]).
 */
class OdoApplication : Application() {

    /**
     * Cold start gets its own trace id: the process is launching, no user is
     * authenticated yet, so this is generated independently of any later
     * login/session trace. Read by [MainActivity] to correlate the end.
     */
    val appSessionId: String = UUID.randomUUID().toString()

    /** The cold-start span — opened in [onCreate], ended in [MainActivity] on first frame. */
    lateinit var coldStartSpan: Span
        private set

    override fun onCreate() {
        super.onCreate()

        // Configure the single logger first, so the Koin graph republishes a ready
        // logger (loggingModule binds HLogger.asLogger()) and APM diagnostics have
        // somewhere to go.
        configureLogging(BuildConfig.DEBUG)

        // Bring APM up before starting the cold-start span: the facade hands back an
        // inert span until init() runs, so this ordering is what makes the span real.
        // It still captures the expensive part of startup — Koin wiring below runs
        // inside the span, and the span only closes on the first drawn frame.
        configureApm(BuildConfig.DEBUG)
        coldStartSpan = APM.startSpan("app_cold_start", traceId = appSessionId)
            .setAttribute("launch_type", "cold")

        // Analytics pipeline — republished into the graph by analyticsModule so
        // features inject a ready AnalyticsTracker.
        configureAnalytics(BuildConfig.DEBUG)

        // Crash reporting — republished by crashReportingModule. Configured before the
        // graph starts for the same reason as APM: the facade hands back an inert
        // recorder until init() runs, and the data layer records non-fatals through it.
        configureCrashReporting(BuildConfig.DEBUG)

        initKoin(
            platformModule = module {
                single { DriverFactory(androidContext()) }
                // The vault stores picked documents through Android APIs, so its store
                // is bound by the feature's own Android module rather than in common code.
                includes(documentVaultAndroidModule)
            },
        ) {
            androidLogger(Level.INFO)
            androidContext(this@OdoApplication)
        }

        HLogger.tag("APP_LIFECYCLE").i("process_created", mapOf("appSessionId" to appSessionId))
    }

    private fun configureLogging(isDebugBuild: Boolean) {
        HLogger.init(
            loggerConfig {
                environment(if (isDebugBuild) LoggerConfig.Environment.DEBUG else LoggerConfig.Environment.PRODUCTION)
                filePath("app_logs.log")
                remoteEndpoint(if (isDebugBuild) null.toString() else "https://logs.fourthfrontier.com/ingest")
                minLevel(if (isDebugBuild) LogLevel.VERBOSE else LogLevel.INFO)
                piiRedaction(true)
            }
        )
    }

    private fun configureApm(isDebugBuild: Boolean) {
        APM.init(
            PerformanceConfig(
                appVersion = BuildConfig.VERSION_NAME,
                deviceModel = Build.MODEL,
                osVersion = "Android ${Build.VERSION.RELEASE}",
                locale = Locale.getDefault().toLanguageTag(),
                isDebug = isDebugBuild,
                onDiagnostic = { Log.w("APM", it) },
            )
        )
    }

    /**
     * Fatal reports are written synchronously to [crashDir] before the process dies, so
     * the path has to be one the app owns — hence the Context-bearing bootstrap rather
     * than a default inside the module.
     */
    private fun configureCrashReporting(isDebugBuild: Boolean) {
        CrashReporter.init(
            CrashConfig(
                appVersion = BuildConfig.VERSION_NAME,
                osVersion = "Android ${Build.VERSION.RELEASE}",
                deviceModel = Build.MODEL,
                crashDirPath = File(filesDir, "crash").absolutePath,
                isDebug = isDebugBuild,
                onDiagnostic = { Log.w("Crash", it) },
            )
        )
    }

    private fun configureAnalytics(isDebugBuild: Boolean) {
        HAnalytics.init(
            AnalyticsConfig(
                appVersion = BuildConfig.VERSION_NAME,
                deviceModel = Build.MODEL,
                osVersion = "Android ${Build.VERSION.RELEASE}",
                locale = Locale.getDefault().toLanguageTag(),
                isDebug = isDebugBuild,
                // Declared up front: debug builds drop anything unregistered, so an event
                // missing from here is one that never reaches a dashboard.
                events = odoAnalyticsEvents,
                onDiagnostic = { Log.w("Analytics", it) },
            )
        )
        // TODO(consent): the real DPDP consent flow must own this gate. Granting
        //  here so the acquisition funnel is observable during development; before
        //  launch, drive setConsent() from the user's recorded consent decision.
        HAnalytics.setConsent(ConsentStatus.GRANTED)
    }
}
