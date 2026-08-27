plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The Garage FeatureEntryProvider is published as a Koin definition so the :app
    // host wires it without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.garage"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Features navigate only
            // through :core:navigation, never by importing another feature — Garage
            // reaches ServiceLog / Documents / Onboarding via the shared OdoDestination keys.
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoScreen, OdoCard, OdoListItem, OdoBadge, OdoChip, OdoButton…)
            // + the Odo theme tokens; re-exports Compose Material 3 transitively.
            implementation(projects.core.designsystem)
            // The shared kernel this feature's own models are built from: the Car
            // aggregate, Document + DocumentValidity, Amount (integer paise), Distance,
            // ServiceCategory / VerificationStatus + the ₹/km/date formatters the UI uses.
            // Brings Arrow transitively via domain (Amount.of returns Either).
            implementation(projects.core.domain)
            // FeatureConfig — the injected replacement for the FeatureFlags consts.
            implementation(projects.core.config)
            // The export pipeline: HtmlToPdf renderer, PlatformFileStore + StorageKey for
            // the written document, and the system share sheet — the same three the
            // service log's record export runs on.
            implementation(projects.core.platform)
            // The auto-odometer engine's public ports (TripTracker, VehicleBondStore) — the
            // garage card/status tile (F9) reads them directly rather than importing
            // :feature:auto-odometer, per the golden rule that features never import
            // features. Core module, so this dependency is allowed.
            implementation(projects.core.triptracker)
            // Observability: structured logging + product analytics, instrumented at the
            // presentation layer (domain stays pure). Interfaces injected; the single
            // config is owned by the app bootstrap.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            // APM spans around the DB writes the ViewModels drive — the feature's
            // telemetry facade owns the plumbing.
            implementation(projects.observability.performance)
            // koinViewModel() in the route hosts, plus koinInject() for the ActiveCarProvider
            // the add-to-history sheet reads to name the car a per-car destination is about.
            implementation(libs.koin.composeViewmodel)
            // LocalDate on the garage's own models (service dates, document expiry).
            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Compose Multiplatform string resources for this feature. Explicit package so the
// generated `Res` is imported predictably from the presentation code.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.garage.resources"
    generateResClass = always
}
