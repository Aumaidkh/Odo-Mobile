plugins {
    // Firebase Performance sink for :observability:performance's SpanSink port.
    // Depends inward on :observability:performance only; nothing in that module
    // knows this one exists.
    alias(libs.plugins.odo.kmpLibrary)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.firebase.performance"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.observability.performance)
            // runCatchingCancellable — rethrows CancellationException instead of
            // swallowing it, unlike stdlib's runCatching.
            implementation(projects.core.common)
        }
    }
}

dependencies {
    // Native Android SDK, not gitlive (see the version catalog comment on
    // libs.firebase.perf) — resolved to a concrete version by the BOM here, same
    // reason :infrastructure:firebase:analytics/crashlytics need it.
    "androidMainImplementation"(platform(libs.firebase.bom))
    "androidMainImplementation"(libs.firebase.perf)
}
