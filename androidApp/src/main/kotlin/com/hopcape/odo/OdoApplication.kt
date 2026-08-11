package com.hopcape.odo

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.hopcape.analytics.api.AnalyticsConfig
import com.hopcape.analytics.api.AnalyticsEventStore
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.HAnalytics
import com.hopcape.crashreporting.api.CrashConfig
import com.hopcape.crashreporting.api.CrashReporter
import com.hopcape.logging.api.FileLoggingConfig
import com.hopcape.logging.api.HLogger
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.LogUploadRunner
import com.hopcape.logging.api.LoggerConfig
import com.hopcape.logging.api.loggerConfig
import com.hopcape.odo.core.common.BuildInfo
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.platform.corePlatformAndroidModule
import com.hopcape.odo.core.platform.logging.AndroidLogFileStore
import com.hopcape.odo.core.triptracker.tripTrackerAndroidModule
import com.hopcape.odo.infrastructure.database.db.DriverFactory
import com.hopcape.odo.infrastructure.firebase.analytics.FirebaseAnalyticsSink
import com.hopcape.odo.infrastructure.firebase.auth.firebaseAuthAndroidModule
import com.hopcape.odo.infrastructure.firebase.crashlytics.FirebaseCrashlyticsSink
import com.hopcape.odo.infrastructure.firebase.performance.FirebasePerformanceSink
import com.hopcape.odo.di.initKoin
import com.hopcape.odo.di.odoAnalyticsEvents
import com.hopcape.performance.api.APM
import com.hopcape.performance.api.PerformanceConfig
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.SpanSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
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
                // Copying a picked file into app storage needs a Context, so the store is
                // bound by the platform module rather than in common code.
                includes(corePlatformAndroidModule)
                includes(tripTrackerAndroidModule)
                // Sending an SMS needs an Activity, which only this bootstrap can supply,
                // so the real PhoneVerifier is bound here rather than in the shared graph.
                // Replaces firebaseAuthModule's unavailable one — the platform module is
                // last in initKoin, so this wins.
                includes(firebaseAuthAndroidModule)
            },
        ) {
            androidLogger(Level.INFO)
            androidContext(this@OdoApplication)
        }

        HLogger.tag("APP_LIFECYCLE").i("process_created", mapOf("appSessionId" to appSessionId))

        // D3 (docs/LOGGING_PLAN.md §1): auto-upload is opt-in in release; debug/internal
        // builds start granted so the periodic path can be exercised without a manual consent
        // flow. "Send diagnostics" (:feature:support) bypasses this gate entirely by design —
        // this only controls the periodic, non-manual path.
        // TODO(consent): the diagnostic-log gate is still not owned by the privacy screen.
        //  Deliberately out of scope there: "Usage analytics" governs product analytics only,
        //  so claiming this one under it would make the switch's copy false.
        KoinPlatform.getKoin().get<LogUploadRunner>().setAutoUploadConsent(granted = BuildConfig.DEBUG)

        applyAnalyticsConsent()

        // Catches up on whatever the durable queue is still holding from the last session
        // — the periodic timer would get to it within flushInterval anyway, but there is
        // no reason to wait once the graph (and the AnalyticsEventStore behind it) is up.
        HAnalytics.flush()
        // ProcessLifecycleOwner, not an Activity callback: a rotation or a second Activity
        // in the same task must not look like a foreground event, and this only fires on a
        // genuine background -> foreground transition of the whole process.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    HAnalytics.flush()
                    // Catches a maintenance window that opened or closed while the app was
                    // backgrounded (docs/APP_STATUS_PLAN.md §5.3). A short-lived scope, same
                    // shape as the one KoinInit's own startup coroutine uses — refresh()
                    // never throws, so there is nothing here for a crash to come from.
                    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                        KoinPlatform.getKoin().get<AppStatusProvider>().refresh()
                    }
                }

                // The app just became killable — one of AsyncSink's five flush triggers
                // (docs/LOGGING_PLAN.md §5). A rotation still looks like onStop+onStart, but
                // flushing early on a rotation is harmless: it just drains the buffer sooner.
                override fun onStop(owner: LifecycleOwner) = HLogger.flush()
            },
        )
    }

    /**
     * Open the analytics gate only as far as the owner has agreed to.
     *
     * The gate starts closed — `ConsentStatus.UNKNOWN` tracks nothing — so this has to run on
     * every launch, not only when the answer is no. It reads the same
     * `AppSettings.privacy.usageAnalytics` the privacy screen writes, which is what makes an
     * opt-out survive a restart rather than lasting one session.
     *
     * A short-lived coroutine because the read is a `Flow` and the graph is only up now, the
     * same shape as the `AppStatusProvider.refresh()` call above. The window before it lands
     * is a few milliseconds of a closed gate, which is the safe direction to be wrong in: an
     * event lost is better than an event nobody agreed to.
     */
    private fun applyAnalyticsConsent() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val settings = KoinPlatform.getKoin().get<AppSettingsRepository>()
            // Not `first()` — the switch can move later in the session, and a one-shot read
            // would leave analytics running until the next launch after an opt-out. The
            // privacy screen also applies it directly for immediacy; this is what keeps the
            // two agreeing if a write lands from anywhere else.
            settings.observe()
                .map { it.privacy.usageAnalytics }
                .distinctUntilChanged()
                .collect { granted ->
                    HAnalytics.setConsent(
                        if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED,
                    )
                }
        }
    }

    /**
     * `logFileStore` is a fresh [AndroidLogFileStore] rather than one resolved from Koin:
     * this runs before [initKoin] (same reason [configureCrashReporting] constructs its sink
     * directly), and it must be — the logger needs to be ready the moment Koin wiring itself
     * wants to log. `corePlatformAndroidModule` binds a second instance over the same "logs"
     * directory for everything downstream of Koin (the upload coordinator, "send
     * diagnostics"); the two only ever disagree if something calls `sealOrphans()` on the
     * Koin one, which nothing does (see that binding's comment).
     */
    private fun configureLogging(isDebugBuild: Boolean) {
        val logFileStore = AndroidLogFileStore(dir = File(filesDir, "logs"))
        HLogger.init(
            loggerConfig {
                environment(if (isDebugBuild) LoggerConfig.Environment.DEBUG else LoggerConfig.Environment.PRODUCTION)
                fileLogging(FileLoggingConfig(store = logFileStore))
                remoteEndpoint(if (isDebugBuild) null.toString() else "https://logs.fourthfrontier.com/ingest")
                minLevel(if (isDebugBuild) LogLevel.VERBOSE else LogLevel.INFO)
                piiRedaction(true)
                // The logger must not report its own failures through itself
                // (docs/LOGGING_PLAN.md §8) — a sink that starts throwing (a full disk, a
                // permission error) goes to Crashlytics as a non-fatal instead of vanishing.
                // Safe to call before configureCrashReporting() runs below: CrashReporter's
                // facade routes to its own pre-init no-op fallback until then, the same
                // pattern HLogger itself uses.
                onInternalError { t -> CrashReporter.recordNonFatal(t, mapOf("component" to "logging")) }
            }
        )
    }

    /**
     * `APM.init` always runs, so spans exist and their own diagnostics still work on a
     * debug build. Whether a span actually reaches Firebase is gated by
     * [BuildInfo.isPerformanceReportingEnabled] (release only) — a debug device shouldn't
     * add its noise to production traces, and this app has no separate debug
     * applicationId sharing the same Firebase project to filter it out instead.
     */
    private fun configureApm(isDebugBuild: Boolean) {
        APM.init(
            PerformanceConfig(
                appVersion = BuildConfig.VERSION_NAME,
                deviceModel = Build.MODEL,
                osVersion = "Android ${Build.VERSION.RELEASE}",
                locale = Locale.getDefault().toLanguageTag(),
                isDebug = isDebugBuild,
                // Constructed directly rather than resolved from Koin — this runs before
                // initKoin() below, and the sink has no dependency that needs the graph.
                destinations = buildApmDestinations(isDebugBuild),
                onDiagnostic = { Log.w("APM", it) },
            )
        )
    }

    private fun buildApmDestinations(isDebugBuild: Boolean): List<SpanSink> =
        if (BuildInfo.isPerformanceReportingEnabled) {
            listOf(
                FirebasePerformanceSink(
                    onDiagnostic = { Log.w("APM", it) },
                    buildType = if (isDebugBuild) "debug" else "release",
                )
            )
        } else {
            emptyList()
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
                // Constructed directly rather than resolved from Koin — this runs before
                // initKoin() below, and the sink has no dependency that needs the graph.
                destinations = listOf(FirebaseCrashlyticsSink(onDiagnostic = { Log.w("Crash", it) })),
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
                // Constructed directly rather than resolved from Koin — this runs before
                // initKoin() below, and the sink has no dependency that needs the graph.
                destinations = listOf(FirebaseAnalyticsSink(onDiagnostic = { Log.w("Analytics", it) })),
                // A provider, not an instance — resolved on first real use, well after
                // initKoin() below has run. KoinPlatform.getKoin() rather than a captured
                // Koin reference because none exists yet at this point in onCreate();
                // initKoin() registers the graph this reads from, the same default
                // instance OdoSyncWorker's KoinComponent resolves against elsewhere.
                eventStore = { KoinPlatform.getKoin().get<AnalyticsEventStore>() },
                onDiagnostic = { Log.w("Analytics", it) },
            )
        )
        // TODO(consent): the real DPDP consent flow must own this gate. Granting
        //  here so the acquisition funnel is observable during development; before
        //  launch, drive setConsent() from the user's recorded consent decision.
        HAnalytics.setConsent(ConsentStatus.GRANTED)
    }
}
