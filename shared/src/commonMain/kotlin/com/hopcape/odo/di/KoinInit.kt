package com.hopcape.odo.di

import com.hopcape.analytics.api.analyticsModule
import com.hopcape.crashreporting.api.crashReportingModule
import com.hopcape.logging.api.loggingModule
import com.hopcape.performance.api.performanceModule
import com.hopcape.odo.core.common.coreCommonModule
import com.hopcape.odo.core.data.coreDataModule
import com.hopcape.odo.core.navigation.coreNavigationModule
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.odo.core.sync.coreSyncModule
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
import com.hopcape.odo.infrastructure.supabase.supabaseModule
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
        coreDataModule,
        // The engine collects its Syncables with getAll(), so it must be listed after the
        // module that registers them.
        coreSyncModule,
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
        // Last of the shared modules, because its whole job is to replace things: the
        // offline remote-data-source fakes from coreDataModule, the always-signed-out
        // SessionStatusProvider from authModule, and coreDataModule's session-only SyncGate.
        // Koin lets a later definition win, so this position *is* the wiring — moving it
        // earlier silently puts the stubs back.
        supabaseModule,
        platformModule,
    )
}.also { application ->
    // The first sync of the session. Here rather than in each platform bootstrap so both
    // get it, and after the graph is built because the scheduler is part of it.
    //
    // Cheap by design: on Android this only enqueues WorkManager work, and on iOS the
    // engine is resolved inside the coroutine — neither touches the database on the
    // startup thread.
    application.koin.get<SyncScheduler>().scheduleStartupSync()
}
