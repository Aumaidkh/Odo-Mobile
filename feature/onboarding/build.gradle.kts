plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // ViewModels and the FeatureEntryProvider are published as Koin definitions
    // so the :app host wires them without depending on onboarding's internals.
    alias(libs.plugins.odo.koin)
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
            // Domain use cases / entities / ports the presentation layer drives.
            // Brings Arrow + coroutines-core transitively via domain's api deps.
            implementation(projects.core.domain)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
