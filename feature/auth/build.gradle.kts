plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The Auth FeatureEntryProvider is published as a Koin definition so the :app
    // host wires it without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.auth"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. On success auth clears
            // itself and lands on Home via the shared OdoDestination keys.
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoScreen, OdoButton, OdoPhoneNumberField, OdoText…) + theme.
            implementation(projects.core.designsystem)
            // koinViewModel() in the route hosts, and the viewModel { } DSL in authModule.
            implementation(libs.koin.composeViewmodel)
            // SessionStatusProvider — the port auth implements so other features can ask
            // "is there a session?" without depending on :feature:auth. `api` so the
            // binding published by authModule stays resolvable from the app's graph.
            api(projects.core.domain)
            // SecureStore — a session's tokens are bearer credentials, so they live in the
            // Keystore/Keychain rather than anywhere the filesystem can hand them over.
            implementation(projects.core.platform)
            // A session that ends on its own does so with no error and no screen; these are
            // what keep that from being invisible.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            implementation(projects.observability.performance)
            // delay() drives the sample "verifying" hand-off to Home.
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Compose Multiplatform string resources for this feature.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.auth.resources"
    generateResClass = always
}
