plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    alias(libs.plugins.odo.koin)
}

kotlin {
    // Only this module's identity lives here; targets, SDK levels, JVM target,
    // the iOS framework and test wiring all come from the odo.kmp.library
    // convention plugin.
    androidLibrary {
        namespace = "com.hopcape.odo.shared"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            // Core Compose UI + lifecycle ViewModel artifacts come from the
            // odo.compose.multiplatform convention plugin. Only module-specific
            // extras are listed here.
            implementation(libs.compose.uiToolingPreview)
            // Functional error handling — Either<DomainError, T> at boundaries.
            // (Koin is supplied by the odo.koin convention plugin.)
            implementation(libs.arrow.core)
            // App navigation (command bus + Nav3 host). Exposes Nav3 transitively.
            implementation(projects.core.navigation)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
