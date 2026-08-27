plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Only this module's identity lives here; targets, SDK levels, JVM target,
    // the iOS framework and test wiring all come from the odo.kmp.library
    // convention plugin.
    androidLibrary {
        namespace = "com.hopcape.odo.shared"
    }

    sourceSets {
        commonMain.dependencies {
            // Core Compose UI + lifecycle ViewModel artifacts and the tooling
            // preview come from the odo.compose.multiplatform convention plugin.
            // Only module-specific extras are listed here.
            //
            // Functional error handling — Either<DomainError, T> at boundaries.
            // (Koin is supplied by the odo.koin convention plugin.)
            implementation(libs.arrow.core)
            // Design system — OdoTheme + brand tokens. Re-exports Material3.
            implementation(projects.core.designsystem)
            // App navigation (command bus + Nav3 host). Exposes Nav3 transitively.
            implementation(projects.core.navigation)
            // Composition root: :shared aggregates the DI graph (initKoin) and the
            // FeatureEntryProviders App() collects, so it depends on the data layer
            // and each wired feature. (The app root may depend on features.)
            implementation(projects.core.data)
            // The sync engine's Koin module. :shared is the composition root, so it is what
            // lists coreSyncModule alongside the module whose Syncables it collects.
            implementation(projects.core.sync)
            // The trip-tracking engine's Koin module + tripTrackerAndroidModule/
            // tripTrackerIosModule the platform bootstraps include alongside the driver.
            implementation(projects.core.triptracker)
            // corePlatformIosModule — the iOS bootstrap lists it alongside the driver.
            implementation(projects.core.platform)
            // The composition root's one decision — open on Welcome or Home — reads
            // OwnerProfile.hasCompletedOnboarding through the existing repository port.
            // Brings coroutines + Arrow along via :core:domain's api dependencies.
            implementation(projects.core.domain)
            // coreConfigModule, listed in initKoin.
            implementation(projects.core.config)
            implementation(projects.feature.auth)
            implementation(projects.feature.autoOdometer)
            implementation(projects.feature.onboarding)
            implementation(projects.feature.servicelog)
            implementation(projects.feature.billscanner)
            implementation(projects.feature.costTracker)
            implementation(projects.feature.documentVault)
            implementation(projects.feature.fairnessCheck)
            implementation(projects.feature.reminders)
            implementation(projects.feature.healthscore)
            implementation(projects.feature.dashboard)
            implementation(projects.feature.garage)
            implementation(projects.feature.profile)
            implementation(projects.feature.refuel)
            implementation(projects.feature.support)
            implementation(projects.feature.timeline)
            implementation(projects.feature.paywall)
            // The SQLDelight database and the LocalDataSource adapters :core:data's
            // repositories depend on. Listed here because :shared is the composition
            // root — databaseInfrastructureModule goes into initKoin before
            // coreSyncModule so its Syncables are registered before the engine collects them.
            implementation(projects.infrastructure.database)
            // Supabase adapters for the remote ports :core:data declares. Listed here
            // because :shared is the composition root — supabaseModule goes into initKoin
            // after coreDataModule so its adapters replace that module's offline fakes.
            implementation(projects.infrastructure.supabase)
            // On-device scan adapters for the ports :core:domain declares — same
            // composition-root reasoning; aiInfrastructureModule goes in after coreDataModule.
            implementation(projects.infrastructure.ai)
            // The store adapters. Same composition-root reasoning as the two above: only
            // the app assembles the graph, so only the app depends on who implements a port.
            implementation(projects.infrastructure.billing)
            // Structured logging — build-type-aware Logger wired via loggingModule.
            implementation(projects.observability.logging)
            // Product analytics — AnalyticsTracker wired via analyticsModule.
            implementation(projects.observability.analytics)
            // FirebaseAnalyticsSink is constructed directly in MainViewController (iOS
            // has no separate app module) — same reason :androidApp depends on it.
            implementation(projects.infrastructure.firebase.analytics)
            // firebaseRemoteConfigModule — the app-status gate's real AppStatusSource,
            // replacing coreDataModule's AlwaysAvailableAppStatusSource. Same
            // composition-root reasoning as :infrastructure.supabase/:infrastructure.ai
            // below: resolved through Koin, not constructed directly, so only :shared
            // (which calls initKoin) needs this dependency.
            implementation(projects.infrastructure.firebase.remoteconfig)
            // firebaseAuthModule — the PhoneVerifier that proves a number before
            // :infrastructure:supabase's bridge trades it for a session. Listed before
            // supabaseModule in initKoin, because the bridge resolves this port.
            implementation(projects.infrastructure.firebase.auth)
            // APM tracer (spans/traces) wired via performanceModule.
            implementation(projects.observability.performance)
            // CrashRecorder wired via crashReportingModule — :core:data's telemetry
            // records non-fatals through it, so the graph must publish one.
            implementation(projects.observability.crashreporting)
            // koinInject()/getKoin()/KoinContext for App(); brings koin-compose in.
            implementation(libs.koin.composeViewmodel)
        }
        commonTest.dependencies {
            // runTest for the start-destination gate, which waits on the first config fetch.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Compose Multiplatform string resources for the app shell — the app-status gate's
// blocked-screen copy (AppGate.kt). No feature owns this; it is app-shell chrome.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.shared.resources"
    generateResClass = always
}
