plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The ViewModel and the FeatureEntryProvider are published as Koin definitions so
    // the :app host wires them without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.advisory"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Features navigate
            // only through :core:navigation, never by importing another feature.
            implementation(projects.core.navigation)
            // Branded UI atoms + the Odo theme tokens; re-exports Compose Material 3.
            implementation(projects.core.designsystem)
            // The shared kernel this feature's estimator reads: Car, ServiceLogEntry,
            // their repository ports, City, and DomainError.
            implementation(projects.core.domain)
            // Sharing the value card is a platform action, so the feature needs the port.
            implementation(projects.core.platform)
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            implementation(projects.observability.performance)
            // LocalDate for the car's age, which is what the depreciation curve is keyed by.
            implementation(libs.kotlinx.datetime)
            // koinViewModel() for the navigation route host (effect -> nav bridge).
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
    packageOfResClass = "com.hopcape.odo.feature.advisory.resources"
    generateResClass = always
}
