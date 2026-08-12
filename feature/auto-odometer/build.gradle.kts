plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The ViewModels and FeatureEntryProvider are published as Koin definitions so
    // the :app host wires them without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.autoodometer"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Features navigate
            // only through :core:navigation, never by importing another feature.
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoScreen, OdoCard, OdoOdometer…) + the Odo theme
            // tokens; re-exports Compose Material 3 transitively.
            implementation(projects.core.designsystem)
            // Shared kernel the feature's use cases orchestrate: Trip, Car, the
            // ServiceLog/reminder kernel, repository ports, DomainError.
            // Feature-specific use cases live HERE (in the feature), not in core.
            implementation(projects.core.domain)
            // The trip-tracking engine's public surface — TripTracker, TrackingReadiness,
            // VehicleBondStore, BondedDeviceCatalog. A :core:* module a feature is
            // explicitly allowed to depend on (docs/AUTO_ODOMETER_PLAN.md §2).
            implementation(projects.core.triptracker)
            // The permission seam (rememberPermissionController, the camera controller's
            // generalised form) the device picker gates BLUETOOTH_CONNECT behind — the
            // same module :feature:billscanner already depends on for the camera permission.
            implementation(projects.core.platform)
            // Observability: structured logging + product analytics + APM spans,
            // instrumented at the presentation layer (domain stays pure).
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            implementation(projects.observability.performance)
            // CrashRecorder for enrollment/catalog-read failures — a non-fatal is the
            // only signal that the Bluetooth catalog or bond store broke.
            implementation(projects.observability.crashreporting)
            // LocalDate/Clock in the use cases + form state.
            implementation(libs.kotlinx.datetime)
            // koinViewModel() for the navigation route hosts (effect -> nav bridge).
            implementation(libs.koin.composeViewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Compose Multiplatform string resources for this feature. Explicit package so the
// generated `Res` is imported predictably from the presentation + UI code.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.autoodometer.resources"
    generateResClass = always
}
