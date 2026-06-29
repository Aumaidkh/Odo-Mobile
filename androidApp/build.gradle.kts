plugins {
    alias(libs.plugins.odo.androidApplication)
    alias(libs.plugins.odo.composeMultiplatform)
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

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}
