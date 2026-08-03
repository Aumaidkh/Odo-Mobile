plugins {
    // Sync contracts + orchestration. Deliberately the emptiest build file in the
    // repo: this module knows nothing about SQLDelight, Supabase, Android or the
    // domain — it only defines the seam (Syncable/Synchronizer) and the order work
    // runs in. :core:data depends on *this*, never the reverse, which is what keeps
    // "repositories sync themselves" from becoming a dependency cycle.
    alias(libs.plugins.odo.kmpLibrary)
    // The engine ships as a Koin module the app bootstrap lists, and it receives its
    // Syncables through getAll() rather than reaching for them.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.core.sync"
    }

    // SyncCursor's timestamps are kotlin.time.Instant, still opt-in on this Kotlin
    // version. Declared once here rather than annotating each declaration.
    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            // Observability only. The rule this module lives by is "no database, no
            // network, no Android" — these four are commonMain-pure interfaces and break
            // none of it. A sync run needs its own span and its own structured line; the
            // Syncables cannot report the run, only their own part of it.
            // `api`, not `implementation`: SyncTelemetry is public and its constructor
            // takes all four, so a module that builds one needs to see the types.
            api(projects.observability.logging)
            api(projects.observability.analytics)
            api(projects.observability.performance)
            api(projects.observability.crashreporting)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    // Still no database, network or Android dependency. If one appears here the boundary
    // has been broken — the engine is supposed to receive its Syncables, not go looking
    // for them.
}
