plugins {
    // No version — build-logic already has AGP on the classpath. No Kotlin plugin
    // either: AGP 9 has Kotlin built in and rejects org.jetbrains.kotlin.android.
    id("com.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * Records the startup baseline profile for :androidApp. Nothing here ships in the APK.
 *
 * A `com.android.test` module because the generator runs in its own process and drives
 * the real app from outside.
 */
android {
    namespace = "com.hopcape.odo.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // The recorder's own floor. The app stays at minSdk 26 — an older device uses
        // the profile, it just cannot record one.
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":androidApp"
}

baselineProfile {
    // Record on the attached phone. A managed emulator is more reproducible, but a
    // profile is only as good as the hardware it was recorded on.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.testExt.junit)
    implementation(libs.androidx.benchmark.macroJunit4)
}
