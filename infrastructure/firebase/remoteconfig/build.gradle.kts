plugins {
    // Firebase Remote Config adapter for :core:domain's AppStatusSource port. Depends
    // inward on :core:domain (the port) and :core:common (runCatchingCancellable) only;
    // nothing in those modules knows this one exists.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.firebase.remoteconfig"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            // runCatchingCancellable / runCatchingCancellableSuspend — rethrow
            // CancellationException instead of swallowing it, unlike stdlib's runCatching.
            implementation(projects.core.common)
            implementation(libs.gitlive.firebase.config)
            // The gateway's onDiagnostic is wired to this in firebaseRemoteConfigModule, the
            // same "a vendor SDK failure is reported, never thrown" contract every other
            // Firebase gateway in this repo already holds.
            implementation(projects.observability.logging)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    // Same reason as :infrastructure:firebase:analytics — the gitlive wrapper's Android
    // artifact depends on the real com.google.firebase:* modules with no version of its
    // own, so the version has to come from the BOM here.
    "androidMainImplementation"(platform(libs.firebase.bom))
}
