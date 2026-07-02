plugins {
    alias(libs.plugins.odo.androidApplication)
    alias(libs.plugins.odo.composeMultiplatform)
    // Application-level startKoin + androidContext (adds koin-android).
    alias(libs.plugins.odo.koin)
}

android {
    // Only this module's identity lives here; SDK levels, version name/code,
    // JVM target, packaging and build types all come from the
    // odo.android.application convention plugin.
    namespace = "com.hopcape.odo"

    defaultConfig {
        applicationId = "com.hopcape.odo"
    }
}

dependencies {
    implementation(projects.shared)
    // The Android SQLDelight DriverFactory (needs a Context) wired into Koin here.
    implementation(projects.core.data)
    implementation(projects.observability.logging)

    implementation(libs.androidx.activity.compose)
    // Compose tooling preview (annotations + debug renderer) is supplied by the
    // odo.compose.multiplatform convention plugin.
}
