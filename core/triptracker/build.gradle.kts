plugins {
    // The trip-tracking engine: state machine, algorithm, signal-source ports, androidMain
    // adapters (S8). Depends inward on :core:domain (the Trip vocabulary) and :core:common
    // only — never :core:data or :infrastructure:database. It reaches persistence solely
    // through the TripRepository and TripSessionStore ports.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.core.triptracker"
    }

    // Duration/Instant thresholds throughout the state machine and config, same opt-in
    // :core:sync uses (kotlinx-datetime's Instant breaks the iOS link on Kotlin 2.4).
    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            // Trip, TripDistance, GeoPoint, TripRepository — the vocabulary the engine
            // (S5) orchestrates. api: TrackingStatus.Tracking exposes TripMode publicly.
            api(projects.core.domain)
            implementation(projects.core.common)
            implementation(projects.observability.logging)
            implementation(projects.observability.analytics)
            implementation(projects.observability.performance)
            implementation(projects.observability.crashreporting)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
