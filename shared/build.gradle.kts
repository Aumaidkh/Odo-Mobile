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
            // corePlatformIosModule — the iOS bootstrap lists it alongside the driver.
            implementation(projects.core.platform)
            // The composition root's one decision — open on Welcome or Home — reads
            // OwnerProfile.hasCompletedOnboarding through the existing repository port.
            // Brings coroutines + Arrow along via :core:domain's api dependencies.
            implementation(projects.core.domain)
            implementation(projects.feature.auth)
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
            implementation(projects.feature.support)
            implementation(projects.feature.timeline)
            implementation(projects.feature.paywall)
            // Supabase adapters for the remote ports :core:data declares. Listed here
            // because :shared is the composition root — supabaseModule goes into initKoin
            // after coreDataModule so its adapters replace that module's offline fakes.
            implementation(projects.infrastructure.supabase)
            // Structured logging — build-type-aware Logger wired via loggingModule.
            implementation(projects.observability.logging)
            // Product analytics — AnalyticsTracker wired via analyticsModule.
            implementation(projects.observability.analytics)
            // APM tracer (spans/traces) wired via performanceModule.
            implementation(projects.observability.performance)
            // CrashRecorder wired via crashReportingModule — :core:data's telemetry
            // records non-fatals through it, so the graph must publish one.
            implementation(projects.observability.crashreporting)
            // koinInject()/getKoin()/KoinContext for App(); brings koin-compose in.
            implementation(libs.koin.composeViewmodel)
        }
    }
}
