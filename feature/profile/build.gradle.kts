plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // The Profile FeatureEntryProvider is published as a Koin definition so the :app
    // host wires it without depending on internals.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.profile"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Profile reaches the
            // Paywall through the shared OdoDestination keys, never by importing it.
            implementation(projects.core.navigation)
            // Branded UI atoms (OdoScreen, OdoCard, OdoSwitch, OdoInputField…) + theme.
            implementation(projects.core.designsystem)
        }
    }
}

// Compose Multiplatform string resources for this feature.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.profile.resources"
    generateResClass = always
}
