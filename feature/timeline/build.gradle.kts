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
            // The shared kernel the feed is assembled from: ActivityEvent +
            // ActivityFeedBuilder, the repository ports behind them, and the value objects
            // (Amount in integer paise, Distance, WorkshopName, WorkDone) plus the
            // ₹/km/date formatters the UI uses.
            implementation(projects.core.domain)
            // Observability: the feature's telemetry facade logs and counts what the tab
            // does. The domain stays pure — instrumentation sits at the presentation layer.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            // Spans for the feed's async work, and the flow trace the facade stamps on
            // every event.
            implementation(projects.observability.performance)
            // koinViewModel()/koinInject() in the route hosts.
            implementation(libs.koin.composeViewmodel)
            // LocalDate on the events (dates + month grouping) and the TimeZone the feed
            // is placed on.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Compose Multiplatform string resources for this feature.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.timeline.resources"
    generateResClass = always
}
