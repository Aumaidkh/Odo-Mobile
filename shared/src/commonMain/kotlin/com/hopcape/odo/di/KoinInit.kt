package com.hopcape.odo.di

import com.hopcape.analytics.api.analyticsModule
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.crashreporting.api.crashReportingModule
import com.hopcape.logging.api.loggingModule
import com.hopcape.performance.api.performanceModule
import com.hopcape.odo.core.common.coreCommonModule
import com.hopcape.odo.core.data.coreDataModule
import com.hopcape.odo.core.navigation.coreNavigationModule
import com.hopcape.odo.core.domain.auth.SessionRestore
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.coreSyncModule
import com.hopcape.odo.core.triptracker.coreTripTrackerModule
import com.hopcape.odo.feature.auth.authModule
import com.hopcape.odo.feature.billscanner.billScannerModule
import com.hopcape.odo.feature.costtracker.costTrackerModule
import com.hopcape.odo.feature.dashboard.dashboardModule
import com.hopcape.odo.feature.documentvault.documentVaultModule
import com.hopcape.odo.feature.fairnesscheck.fairnessCheckModule
import com.hopcape.odo.feature.garage.garageModule
import com.hopcape.odo.feature.healthscore.healthScoreModule
import com.hopcape.odo.feature.paywall.paywallModule
import com.hopcape.odo.feature.profile.profileModule
import com.hopcape.odo.feature.reminders.remindersModule
import com.hopcape.odo.feature.onboarding.onboardingModule
import com.hopcape.odo.feature.servicelog.serviceLogModule
import com.hopcape.odo.feature.support.supportModule
import com.hopcape.odo.feature.timeline.timelineModule
import com.hopcape.odo.infrastructure.ai.aiInfrastructureModule
import com.hopcape.odo.infrastructure.database.databaseInfrastructureModule
import com.hopcape.odo.infrastructure.supabase.supabaseModule
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
        supportModule,
        timelineModule,
        paywallModule,
        // After every feature module, because its whole job is to replace things: the
        // offline remote-data-source fakes from coreDataModule, the always-signed-out
        // SessionStatusProvider from authModule, and coreDataModule's session-only SyncGate.
        // Koin lets a later definition win, so this position *is* the wiring — moving it
        // earlier silently puts the stubs back.
        supabaseModule,
        // After coreDataModule for the same reason: its on-device BillExtractor binding
        // replaces that module's UnconfiguredBillExtractor stub.
        aiInfrastructureModule,
        platformModule,
    )
}.also { application ->
    // Restore the session, then ask for the launch's first sync — in that order, because a
    // sync that starts before the session is loaded sees no token and skips, and nothing
    // retries it until the next launch.
    //
    // Off the startup thread: reading the session touches the Keystore/Keychain. The
    // scheduling that follows is cheap by design — on Android it only enqueues WorkManager
    // work, and on iOS the engine is resolved inside the coroutine.
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
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
    }
}

/* Keys for the one non-fatal the startup path can record. */
private const val STAGE = "stage"
private const val RESTORE = "session_restore"
