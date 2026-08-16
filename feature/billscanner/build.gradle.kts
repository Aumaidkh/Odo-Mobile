plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The ServiceLog ViewModels and FeatureEntryProvider are published as Koin
    // definitions so the :app host wires them without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.billscanner"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Features navigate
            // only through :core:navigation, never by importing another feature.
            implementation(projects.core.navigation)
            // IdGenerator — every scan mints a ScanId. Reachable through :core:domain's
            // `api`, but declared because this module reads it directly.
            implementation(projects.core.common)
            // Branded UI atoms (OdoScreen, OdoCard, OdoInputField, OdoChip, OdoBadge…)
            // + the Odo theme tokens; re-exports Compose Material 3 transitively.
            implementation(projects.core.designsystem)
            // Shared kernel the feature's use cases orchestrate: the ServiceLog
            // entity + value objects, the ServiceLogRepository port, and DomainError.
            // Feature-specific use cases live HERE (in the feature), not in core.
            // Brings Arrow + coroutines-core + IdGenerator transitively via domain.
            implementation(projects.core.domain)
            // The camera + camera-permission seams, and reading a captured photo back.
            implementation(projects.core.platform)
            // Observability: structured logging + product analytics, instrumented at
            // the presentation layer (domain stays pure). Interfaces injected; the
            // single config is owned by the app bootstrap.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            // Spans around the extraction, the saves and the payment — every one of them is
            // asynchronous, and the scan is the slowest thing the app does.
            implementation(projects.observability.performance)
            // LocalDate/Clock in the add/update commands + use cases + form VM.
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
    packageOfResClass = "com.hopcape.odo.feature.billscanner.resources"
    generateResClass = always
}
