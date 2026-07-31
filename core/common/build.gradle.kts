plugins {
    // Pure utilities module (Either re-export, ID generation, Clock).
    // No Compose, no domain — it sits at the very bottom of the stack so
    // any module may depend on it. All KMP/Android/iOS boilerplate comes from the
    // odo.kmp.library convention plugin; only identity + deps live here.
    alias(libs.plugins.odo.kmpLibrary)
    // Koin, for the one module that publishes the shared IdGenerator + Clock.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.core.common"
    }

    sourceSets {
        commonMain.dependencies {
            // Arrow is `api` so every dependent (e.g. :core:domain) gets
            // Either / EitherNel through this single dependency — boundary error
            // handling is a shared concern, not re-declared per module.
            api(libs.arrow.core)
        }
    }
}
