plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The Profile FeatureEntryProvider is published as a Koin definition so the :app
    // host wires it without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.profile"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Profile reaches the
            // Paywall through the shared OdoDestination keys, never by importing it.
            // BuildInfo — decides whether the profile footer shows the build number.
            // Reachable through :core:domain's `api`, but declared because this module
            // uses it directly.
            implementation(projects.core.common)
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoScreen, OdoCard, OdoSwitch, OdoInputField…) + theme.
            implementation(projects.core.designsystem)
            // The shared kernel these use cases orchestrate: OwnerProfile + its value
            // objects, AppSettings + the settings port, DistanceUnit/FuelEfficiencyUnit,
            // ProEntitlement and SessionStatusProvider. Brings Arrow transitively.
            implementation(projects.core.domain)
            // FeatureConfig — the injected replacement for the FeatureFlags consts.
            implementation(projects.core.config)
            // The picker for a profile photo + the store that copies it into app storage.
            implementation(projects.core.platform)
            // Observability: structured logging + product analytics, instrumented at the
            // presentation layer (domain stays pure) behind ProfileTelemetry.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            // APM spans around the settings and profile writes the ViewModels drive.
            implementation(projects.observability.performance)
            // koinViewModel() in the route hosts.
            implementation(libs.koin.composeViewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Compose Multiplatform string resources for this feature.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.profile.resources"
    generateResClass = always
}
