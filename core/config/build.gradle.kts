plugins {
    // commonMain-pure: no androidMain, no iosMain. A config declaration is
    // platform-independent by definition, and there is nothing here that could
    // need a platform implementation.
    alias(libs.plugins.odo.kmpLibrary)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
    // The registry and resolver are bound here, and every generated group's Koin module
    // is emitted into whichever module declares it.
    alias(libs.plugins.odo.koin)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.core.config"
    }

    sourceSets {
        commonMain.dependencies {
            // Flow, for the observe() read model. The only production dependency
            // this module has. It deliberately does not depend on :core:common —
            // nothing here needs it yet, and staying at the very bottom of the
            // stack is what lets :core:domain take this module with no cycle.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
