plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.crashreporting"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        // androidMain / iosMain need no extra deps: the Android actuals use the
        // JDK (java.io.File, Thread) and the iOS actuals are no-ops. No
        // coroutines dependency — the crash-capture path is synchronous by
        // design (the process is dying; an async dispatcher can't be trusted).
        // commonTest: kotlin-test comes from odo.kmp.test; koin-test from odo.koin.
    }
}
