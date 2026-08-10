plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The Support FeatureEntryProvider is published as a Koin definition so the :app
    // host wires it without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.support"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Support is reached from
            // Profile through the shared OdoDestination keys, never by an import.
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoCard, OdoBadge, OdoChip, OdoEmptyState…) + theme.
            implementation(projects.core.designsystem)
            // AppInfo — the version and build number the help sheet's footer shows, read
            // from the installed package rather than baked in at compile time.
            implementation(projects.core.platform)
            // LogUploadScheduler — "Send diagnostics" (docs/LOGGING_PLAN.md §9, L9).
            implementation(projects.observability.logging)
        }
    }
}

// Compose Multiplatform string resources for this feature.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.support.resources"
    generateResClass = always
}
