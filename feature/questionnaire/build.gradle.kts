plugins {
    alias(libs.plugins.odo.kmpLibrary)
    // The registry holds StringResource labels and ImageVector icons, so it needs Compose
    // even though this slice draws nothing yet.
    alias(libs.plugins.odo.composeMultiplatform)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring all come
    // from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.feature.questionnaire"
    }

    sourceSets {
        commonMain.dependencies {
            // Icons and the Odo theme tokens the option cards use.
            implementation(projects.core.designsystem)
            // QuestionKey and the answer types the options map to.
            implementation(projects.core.domain)
            // Nav3 command bus + entry-provider registration. Features navigate only through
            // :core:navigation, never by importing another feature.
            implementation(projects.core.navigation)
            // The telemetry facade's two ports. Interfaces only; the app bootstrap configures them.
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            // koinViewModel() for the route.
            implementation(libs.koin.composeViewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Explicit package so the generated `Res` is imported predictably.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.feature.questionnaire.resources"
    generateResClass = always
}
