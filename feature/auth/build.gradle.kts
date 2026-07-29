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
            // SessionStatusProvider — the port auth implements so other features can ask
            // "is there a session?" without depending on :feature:auth. `api` so the
            // binding published by authModule stays resolvable from the app's graph.
            api(projects.core.domain)
            // delay() drives the sample "verifying" hand-off to Home.
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// Compose Multiplatform string resources for this feature.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.auth.resources"
    generateResClass = always
}
