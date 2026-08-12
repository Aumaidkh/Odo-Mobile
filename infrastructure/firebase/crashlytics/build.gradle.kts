plugins {
    // Firebase Crashlytics sink for :observability:crashreporting's CrashSink
    // port. Depends inward on :observability:crashreporting only; nothing in
    // that module knows this one exists.
    alias(libs.plugins.odo.kmpLibrary)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.firebase.crashlytics"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.observability.crashreporting)
            // runCatchingCancellable — rethrows CancellationException instead of
            // swallowing it, unlike stdlib's runCatching.
            implementation(projects.core.common)
            implementation(libs.gitlive.firebase.crashlytics)
        }
    }
}

dependencies {
    // Same reason as :infrastructure:firebase:analytics — the gitlive wrapper's
    // Android artifact depends on the real com.google.firebase:* modules with no
    // version of its own, so the version has to come from the BOM here.
    "androidMainImplementation"(platform(libs.firebase.bom))
}
