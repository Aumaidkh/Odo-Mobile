plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The ViewModels and the FeatureEntryProvider are published as Koin definitions
    // so the :app host wires them without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.refuel"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration, and the draft key every
            // capture channel hands over. Features navigate only through :core:navigation.
            implementation(projects.core.navigation)
            // IdGenerator — every logged fill mints a FuelFillId.
            implementation(projects.core.common)
            // Branded UI atoms, including OdoOdometer: the confirm step never hand-rolls
            // a control that :core:designsystem already owns.
            implementation(projects.core.designsystem)
            // The shared kernel this feature orchestrates: FuelFill, FuelFillDraft, the
            // fuel-price and odometer ports, and the three pure calculators. The feature's
            // own use cases live HERE, not in core.
            implementation(projects.core.domain)
            // The notification-access seam the auto-detect opt-in checks and opens.
            implementation(projects.core.platform)
            // Observability, instrumented at the presentation layer; the domain stays pure.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            implementation(projects.observability.performance)
            // LocalDate/Clock in the use cases and the draft-building.
            implementation(libs.kotlinx.datetime)
            // koinViewModel() for the navigation route hosts.
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
    packageOfResClass = "com.hopcape.odo.feature.refuel.resources"
    generateResClass = always
}
