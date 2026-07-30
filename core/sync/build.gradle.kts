plugins {
    // Sync contracts + orchestration. Deliberately the emptiest build file in the
    // repo: this module knows nothing about SQLDelight, Supabase, Android or the
    // domain — it only defines the seam (Syncable/Synchronizer) and the order work
    // runs in. :core:data depends on *this*, never the reverse, which is what keeps
    // "repositories sync themselves" from becoming a dependency cycle.
    alias(libs.plugins.odo.kmpLibrary)
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

    // No dependencies by design. If this file ever grows a database, network or
    // Android dependency, the boundary has been broken — the engine is supposed to
    // receive its Syncables, not go looking for them.
}
