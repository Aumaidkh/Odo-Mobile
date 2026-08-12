plugins {
    // Firebase Analytics sink for :observability:analytics's AnalyticsSink port.
    // Depends inward on :observability:analytics only; nothing in that module
    // knows this one exists.
    alias(libs.plugins.odo.kmpLibrary)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.firebase.analytics"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.observability.analytics)
            // runCatchingCancellable — rethrows CancellationException instead of
            // swallowing it, unlike stdlib's runCatching.
            implementation(projects.core.common)
            implementation(libs.gitlive.firebase.analytics)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    // The gitlive wrapper's Android artifact depends on the real
    // com.google.firebase:* modules with no version of its own — Gradle does
    // not honor the import-scope BOM declared in its transitive POM, so the
    // version has to come from here. `platform()` needs the classic
    // DependencyHandler, which the KMP sourceSets.dependencies {} block above
    // doesn't expose, hence the string-configuration form.
    "androidMainImplementation"(platform(libs.firebase.bom))
}
