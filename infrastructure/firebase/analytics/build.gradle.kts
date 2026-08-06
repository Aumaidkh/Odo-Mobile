plugins {
    // Data layer: SQLDelight-backed repository implementations + mappers. The
    // local DB is the offline-first source of truth. Depends inward on
    // :core:domain only (ports + entities); domain never sees a generated row.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // DTOs for the sync payload and for columns holding a structured value.
    alias(libs.plugins.kotlinSerialization)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.firebase.analytics"
    }

    // DriverFactory is an expect/actual *class* (the platform driver needs a
    // Context on Android) — opt in to the stable-enough Beta feature so the
    // build stays warning-free.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.observability.analytics)
            implementation(libs.gitlive.firebase.analytics)
        }
        androidMain.dependencies {
        }
        iosMain.dependencies {
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
