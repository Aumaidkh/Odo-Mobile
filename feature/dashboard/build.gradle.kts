plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The Dashboard ViewModels and FeatureEntryProvider are published as Koin
    // definitions so the :app host wires them without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.dashboard"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Features navigate
            // only through :core:navigation, never by importing another feature.
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoScreen, OdoCard, OdoInputField, OdoChip, OdoBadge…)
            // + the Odo theme tokens; re-exports Compose Material 3 transitively.
            implementation(projects.core.designsystem)
            // Shared kernel the Dashboard's aggregation use cases read from. As an
            // aggregator screen (Home health score + cross-feature insights, Garage
            // overview) it NEVER imports a sibling :feature:* — instead it composes
            // the read-model repository ports each feature exposes in :core:domain
            // (health score, cost summary, reminders, documents…) plus DomainError.
            // The aggregation use cases live HERE (in the feature), not in core.
            // Brings Arrow + coroutines-core + IdGenerator transitively via domain.
            implementation(projects.core.domain)
            // The auto-odometer engine's public ports (TripTracker, VehicleBondStore) —
            // read for the Home discoverability card, same precedent as :feature:garage:
            // ports from a :core: module, never an import of :feature:auto-odometer.
            implementation(projects.core.triptracker)
            // Observability: structured logging + product analytics, instrumented at
            // the presentation layer (domain stays pure). Interfaces injected; the
            // single config is owned by the app bootstrap.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            // Spans for the aggregation's async work, and the flow trace the telemetry
            // facade stamps on every event.
            implementation(projects.observability.performance)
            // LocalDate/TimeZone for the day every rule is resolved against, and the
            // Clock the use case reads it from.
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
    packageOfResClass = "com.hopcape.odo.feature.dashboard.resources"
    generateResClass = always
}
