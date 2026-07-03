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
        namespace = "com.hopcape.odo.feature.servicelog"
    }

    sourceSets {
        commonMain.dependencies {
            // Nav3 command bus + entry-provider registration. Features navigate
            // only through :core:navigation, never by importing another feature.
            implementation(projects.core.navigation)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
