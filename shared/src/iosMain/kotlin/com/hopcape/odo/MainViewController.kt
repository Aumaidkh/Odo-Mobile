package com.hopcape.odo

import androidx.compose.ui.window.ComposeUIViewController
import com.hopcape.analytics.api.AnalyticsConfig
import com.hopcape.analytics.api.AnalyticsEventStore
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.HAnalytics
import com.hopcape.logging.api.HLogger
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.LoggerConfig
import com.hopcape.odo.di.initKoin
import com.hopcape.odo.di.odoAnalyticsEvents
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.platform.corePlatformIosModule
import com.hopcape.odo.core.triptracker.tripTrackerIosModule
import com.hopcape.odo.infrastructure.database.db.DriverFactory
import com.hopcape.odo.infrastructure.firebase.analytics.FirebaseAnalyticsSink
import com.hopcape.odo.infrastructure.firebase.analytics.configureFirebaseForIos
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.preferredLanguages
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIDevice

private var koinStarted = false

/**
 * iOS Compose entry point. Ensures logging, analytics, and the Koin graph are up before
 * rendering. The platform module carries the bits common code cannot build itself: the
 * native SQLDelight [DriverFactory] (no Context needed here) and the platform file store.
 * The [koinStarted] latch keeps a warm relaunch from starting twice.
 *
 * Logging is configured here (once) via [HLogger.init]; `loggingModule` then republishes
 * that same logger into the graph, mirroring the Android bootstrap. Analytics is
 * configured the same way and for the same reason as [com.hopcape.odo.OdoApplication] —
 * `HAnalytics.init` runs before [initKoin], so `analyticsModule`'s first injected
 * `AnalyticsTracker` already hits the real pipeline.
 *
 * APM and crash reporting have no iOS bootstrap yet — a separate gap from analytics.
 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController {
    if (!koinStarted) {
        val isDebug = Platform.isDebugBinary
        HLogger.init(
            LoggerConfig(
                environment = if (isDebug) LoggerConfig.Environment.DEBUG else LoggerConfig.Environment.PRODUCTION,
                filePath = "app_logs.log",
                minLevel = if (isDebug) LogLevel.VERBOSE else LogLevel.INFO,
            )
        )
        configureAnalytics(isDebug)
        initKoin(
            platformModule = module {
                single { DriverFactory() }
                includes(corePlatformIosModule)
                includes(tripTrackerIosModule)
            },
        )
        koinStarted = true

        // Catches up on whatever the durable queue is still holding from the last
        // session — the periodic timer would get to it within flushInterval anyway, but
        // there is no reason to wait once the graph (and the AnalyticsEventStore behind
        // it) is up.
        HAnalytics.flush()
        // didBecomeActive, not willEnterForeground: the latter also fires while an app
        // switcher or control-center overlay is shown, before the app is really usable
        // again.
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { HAnalytics.flush() }
    }
    App()
}

private fun configureAnalytics(isDebugBuild: Boolean) {
    val onFirebaseDiagnostic: (String) -> Unit = { HLogger.tag("Analytics").w(it) }
    // Firebase has no iOS auto-init the way Android's FirebaseInitProvider does, so it is
    // configured first. When there is no GoogleService-Info.plist (this repo's current
    // state), the sink is simply left out of destinations rather than added and left to
    // fail against an unconfigured SDK.
    val firebaseReady = configureFirebaseForIos(onFirebaseDiagnostic)
    val destinations = if (firebaseReady) listOf(FirebaseAnalyticsSink(onDiagnostic = onFirebaseDiagnostic)) else emptyList()

    HAnalytics.init(
        AnalyticsConfig(
            appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
                ?: UNKNOWN_APP_VERSION,
            deviceModel = UIDevice.currentDevice.model,
            osVersion = "${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}",
            locale = NSLocale.preferredLanguages.firstOrNull() as? String ?: DEFAULT_LOCALE,
            isDebug = isDebugBuild,
            // Declared up front: debug builds drop anything unregistered, so an event
            // missing from here is one that never reaches a dashboard.
            events = odoAnalyticsEvents,
            // Constructed directly rather than resolved from Koin — this runs before
            // initKoin() below, and the sink has no dependency that needs the graph.
            destinations = destinations,
            // A provider, not an instance — resolved on first real use, well after
            // initKoin() below has run. KoinPlatform.getKoin() rather than a captured
            // Koin reference because none exists yet at this point in the bootstrap.
            eventStore = { KoinPlatform.getKoin().get<AnalyticsEventStore>() },
            onDiagnostic = onFirebaseDiagnostic,
        )
    )
    // The gate starts closed (UNKNOWN tracks nothing) and is opened from the owner's stored
    // answer, the same `AppSettings.privacy.usageAnalytics` the privacy screen writes.
    //
    // A one-shot read rather than the Android side's live collection: this bootstrap has no
    // long-lived scope to hang a collector on, and the privacy screen applies the change
    // directly when the switch moves, so the only gap is a second app entry point turning it
    // off — which iOS does not have.
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        val granted = KoinPlatform.getKoin().get<AppSettingsRepository>()
            .observe()
            .first()
            .privacy
            .usageAnalytics
        HAnalytics.setConsent(if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED)
    }
}

private const val UNKNOWN_APP_VERSION = "—"
private const val DEFAULT_LOCALE = "en"
