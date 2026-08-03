plugins {
    alias(libs.plugins.odo.kmpLibrary)
    // The file picker is a @Composable expect function — the platform seam is reached
    // from UI code, so this module compiles Compose.
    alias(libs.plugins.odo.composeMultiplatform)
    // The platform bindings ship as Koin modules the app bootstrap lists.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and Android test wiring
    // all come from the odo.kmp.library convention plugin — only identity here.
    androidLibrary {
        namespace = "com.hopcape.odo.core.platform"
    }

    sourceSets {
        commonMain.dependencies {
            // DomainError + Arrow's Either — a store that cannot write says so in the
            // same vocabulary as every other failing port.
            implementation(projects.core.domain)
            // withContext(Dispatchers.IO) — the Keystore does real crypto work off the main thread.
            implementation(libs.kotlinx.coroutines.core)
            // SyncScheduler + SyncEngine — this module supplies the platform scheduler.
            implementation(projects.core.sync)
        }
        commonTest.dependencies {
            // runTest + the virtual clock the scheduler's debounce is tested against.
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            // rememberLauncherForActivityResult for the system document picker.
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.work.runtime)
            // SMS Retriever, so an OTP can be read without the READ_SMS permission.
            implementation(libs.playServices.auth)
        }
    }
}
