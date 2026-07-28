plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The Timeline FeatureEntryProvider is published as a Koin definition so the :app
    // host wires it without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.timeline"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Timeline is an aggregator:
            // it reaches ServiceLog (entry detail + share) / BillScanner through the
            // shared OdoDestination keys, never by importing another feature.
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoScreen, OdoCard, OdoBadge, OdoCheckbox, OdoSwitch…)
            // + the Odo theme tokens; re-exports Compose Material 3 transitively.
            implementation(projects.core.designsystem)
            // The shared kernel this feature's own timeline model is built from:
            // Amount (integer paise), Distance, WorkshopName, ServiceLogId,
            // VerificationStatus, RecordScore + the ₹/km/date formatters the UI uses.
            implementation(projects.core.domain)
            // LocalDate on TimelineEvent (event dates + month grouping).
            implementation(libs.kotlinx.datetime)
        }
    }
}

// Compose Multiplatform string resources for this feature.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.timeline.resources"
    generateResClass = always
}
