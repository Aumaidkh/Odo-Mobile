package com.hopcape.odo.di

import com.hopcape.analytics.api.analyticsModule
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.crashreporting.api.crashReportingModule
import com.hopcape.logging.api.LogUploadScheduler
import com.hopcape.logging.api.loggingModule
import com.hopcape.performance.api.performanceModule
import com.hopcape.odo.core.common.coreCommonModule
import com.hopcape.odo.core.config.coreConfigModule
import com.hopcape.odo.core.data.coreDataModule
import com.hopcape.odo.core.navigation.coreNavigationModule
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import com.hopcape.odo.core.domain.auth.SessionRestore
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.coreSyncModule
import com.hopcape.odo.core.triptracker.coreTripTrackerModule
import com.hopcape.odo.feature.advisory.advisoryModule
import com.hopcape.odo.feature.autoodometer.di.autoOdometerModule
import com.hopcape.odo.feature.auth.authModule
import com.hopcape.odo.feature.billscanner.billScannerModule
import com.hopcape.odo.feature.challan.di.challanModule
import com.hopcape.odo.feature.costtracker.costTrackerModule
import com.hopcape.odo.feature.dashboard.dashboardModule
import com.hopcape.odo.feature.documentvault.documentVaultModule
import com.hopcape.odo.feature.fairnesscheck.fairnessCheckModule
import com.hopcape.odo.feature.garage.garageModule
import com.hopcape.odo.feature.healthscore.healthScoreModule
import com.hopcape.odo.feature.paywall.paywallModule
import com.hopcape.odo.feature.profile.profileModule
import com.hopcape.odo.feature.refuel.domain.RefuelDetectionWorker
import com.hopcape.odo.feature.refuel.refuelModule
import com.hopcape.odo.feature.reminders.remindersModule
import com.hopcape.odo.feature.onboarding.onboardingModule
import com.hopcape.odo.feature.questionnaire.questionnaireModule
import com.hopcape.odo.feature.servicelog.serviceLogModule
import com.hopcape.odo.feature.support.supportModule
import com.hopcape.odo.feature.timeline.timelineModule
import com.hopcape.odo.infrastructure.ai.aiInfrastructureModule
import com.hopcape.odo.infrastructure.billing.billingInfrastructureModule
import com.hopcape.odo.infrastructure.database.databaseInfrastructureModule
import com.hopcape.odo.infrastructure.firebase.auth.firebaseAuthModule
import com.hopcape.odo.infrastructure.firebase.remoteconfig.firebaseRemoteConfigModule
import com.hopcape.odo.infrastructure.supabase.config.supabaseConfigModule
import com.hopcape.odo.infrastructure.supabase.supabaseModule
import com.hopcape.odo.preview.filePreviewModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts the app's Koin graph — the single composition-root entry point every
 * platform bootstrap calls exactly once before the first frame.
 *
 * The **shared** modules (navigation bus, data layer, and each wired feature) are
 * assembled here; platform-specific bits come in through [platformModule] and
 * [declaration]:
 *
 *  - Android — `OdoApplication` passes `androidContext()` + a module supplying the
 *    Context-bearing SQLDelight `DriverFactory`.
 *  - iOS — `MainViewController` passes the native `DriverFactory` module.
 *
 * Logging, analytics, and performance monitoring are configured out-of-band by the
 * platform's single `HLogger.init(...)` / `HAnalytics.init(...)` / `APM.init(...)` /
 * `CrashReporter.init(...)` calls (which pick debug vs production by build type); [loggingModule],
 * [analyticsModule], [performanceModule] and [crashReportingModule] only republish that
 * one logger / tracker / tracer / recorder into the graph, so no build-type flag is
 * needed here.
 *
 * Adding a feature to the running app is then two lines: `implementation` its
 * module here and list its Koin module below — `App()` discovers its screens via
 * `getAll<FeatureEntryProvider>()`, so no other file changes.
 */
fun initKoin(
    platformModule: Module,
    declaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    declaration()
    modules(
        coreCommonModule,
        loggingModule,
        analyticsModule,
        performanceModule,
        crashReportingModule,
        coreNavigationModule,
        // The SQLDelight database and the LocalDataSource adapters coreDataModule's
        // repositories depend on.
        databaseInfrastructureModule,
        coreDataModule,
        // The engine collects its Syncables with getAll(), so it must be listed after the
        // modules that register them.
        coreSyncModule,
        coreTripTrackerModule,
        // Depends transitively on coreDataModule + coreTripTrackerModule +
        // databaseInfrastructureModule, so it must come after all three.
        autoOdometerModule,
        authModule,
        onboardingModule,
        serviceLogModule,
        billScannerModule,
        costTrackerModule,
        documentVaultModule,
        fairnessCheckModule,
        remindersModule,
        healthScoreModule,
        dashboardModule,
        garageModule,
        profileModule,
        questionnaireModule,
        refuelModule,
        supportModule,
        timelineModule,
        paywallModule,
        // The shared file viewer's entry. Not a feature module — it registers one destination
        // that the vault, the service log and the scanner all navigate to.
        filePreviewModule,
        // Before supabaseModule, not after — this one replaces nothing. It publishes the
        // PhoneVerifier that supabaseModule's bridge gateway resolves when it is built, so
        // it has to already be in the graph by then.
        firebaseAuthModule,
        // After every feature module, because its whole job is to replace things: the
        // offline remote-data-source fakes from coreDataModule, the always-signed-out
        // SessionStatusProvider from authModule, and coreDataModule's session-only SyncGate.
        // Koin lets a later definition win, so this position *is* the wiring — moving it
        // earlier silently puts the stubs back.
        supabaseModule,
        // After coreDataModule for the same reason: its on-device BillExtractor binding
        // replaces that module's UnconfiguredBillExtractor stub.
        aiInfrastructureModule,
        // The config registry and resolver. Listed after every module that could
        // register a ConfigContribution, and immediately before the module that supplies
        // the real ConfigSource — the Firebase one replaces its no-backend defaults.
        coreConfigModule,
        // Immediately after coreConfigModule, and that position is the wiring: feature
        // flags live in the `app_config` table now, and this replaces that module's
        // NoRemoteConfigSource and ConfigRefresher.None. Listed inside supabaseModule
        // it would be overridden by those defaults a few lines later.
        supabaseConfigModule,
        // Same reason again: its AppStatusSource binding replaces coreDataModule's
        // AlwaysAvailableAppStatusSource, which blocks nothing. It no longer supplies
        // the ConfigSource — the line above does.
        firebaseRemoteConfigModule,
        // After coreDataModule for the same reason: from S6 its EntitlementSource binding
        // replaces that module's FreePlanEntitlementSource. Today it only configures the
        // RevenueCat SDK, which it does while Koin starts.
        billingInfrastructureModule,
        // After coreDataModule, whose repositories its estimate reads. It registers one
        // destination and nothing else replaces anything, so the position is not load-bearing.
        advisoryModule,
        platformModule,
        challanModule
    )
}.also { application ->
    // Restore the session, then ask for the launch's first sync — in that order, because a
    // sync that starts before the session is loaded sees no token and skips, and nothing
    // retries it until the next launch.
    //
    // Off the startup thread: reading the session touches the Keystore/Keychain. The
    // scheduling that follows is cheap by design — on Android it only enqueues WorkManager
    // work, and on iOS the engine is resolved inside the coroutine.
    val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Detection collects for the app's lifetime rather than as part of the startup sequence:
    // it is a subscription, not a step, and it must not sit between the session restore and
    // the first sync. While SMART_REFUEL_DETECT_ENABLED is false this returns immediately.
    application.koin.get<RefuelDetectionWorker>().start(startupScope)
    startupScope.launch {
        // Restoring is allowed to fail — a session that will not decrypt is reported as no
        // session, which sends the owner to sign in again. What must not happen is losing
        // the sync request with it: a throw here would end the coroutine and this launch
        // would never sync, with nothing to show why.
        try {
            application.koin.get<SessionRestore>().restore()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            application.koin.get<CrashRecorder>().recordNonFatal(e, mapOf(STAGE to RESTORE))
        }
        application.koin.get<SyncScheduler>().scheduleStartupSync()
        // Rebuild the document-expiry notifications. Scheduled jobs survive a reboot but not
        // a reinstall or a restore-from-backup, and a document filed before this existed has
        // no job at all — so every launch re-derives the whole schedule from the database.
        application.koin.get<DocumentReminderScheduler>().refresh()
        // Unconditional: LogUploadCoordinator gates on consent per run (D3, plan §1), so
        // there is nothing to toggle here when consent changes — the next scheduled run
        // just starts working once it is granted. KEEP-policy inside the scheduler makes a
        // repeat call on every launch a no-op once the periodic job already exists.
        application.koin.get<LogUploadScheduler>().schedulePeriodic()
        // The app-status gate's first real answer (docs/APP_STATUS_PLAN.md §5.3). Never
        // throws — every failure inside it already collapses to "keep the fail-open
        // default" — so unlike SessionRestore above, no try/catch is needed here.
        application.koin.get<AppStatusProvider>().refresh()
    }
}

/* Keys for the one non-fatal the startup path can record. */
private const val STAGE = "stage"
private const val RESTORE = "session_restore"
