plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // ViewModels and the FeatureEntryProvider are published as Koin definitions
    // so the :app host wires them without depending on onboarding's internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
    // Generates OnboardingConfig's impl, flows, contribution and Koin module.
    alias(libs.plugins.odo.config)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.onboarding"
    }

    sourceSets {
        commonMain.dependencies {
            // Compose UI surface + lifecycle ViewModel artifacts are supplied by
            // the odo.compose.multiplatform convention plugin.
            //
            // Nav3 command bus + entry-provider registration. Features navigate
            // only through :core:navigation, never by importing another feature.
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoScreen, OdoButton, OdoInputField, OdoDropdownField…)
            // + the Odo theme tokens; re-exports Compose Material 3 transitively.
            implementation(projects.core.designsystem)
            // OdoVideoPlayer — the video intro's clips.
            implementation(projects.core.platform)
            // Domain use cases / entities / ports the presentation layer drives.
            // Brings Arrow + coroutines-core transitively via domain's api deps.
            implementation(projects.core.domain)
            // Observability: structured logging, product analytics, and APM spans —
            // instrumented at this feature/presentation layer (domain stays pure).
            // Consumers inject the Logger / AnalyticsTracker / PerformanceTracer
            // interfaces; the facades' single config is owned by the app bootstrap.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            implementation(projects.observability.performance)
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
    packageOfResClass = "com.hopcape.odo.feature.onboarding.resources"
    generateResClass = always
}
