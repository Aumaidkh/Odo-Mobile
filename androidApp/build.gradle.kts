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
        // On-device (instrumented) tests. The end-to-end onboarding test drives the real
        // app — real Koin graph, real SQLite, real navigation — so it has to run here
        // rather than on the JVM.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.shared)
    // The Android SQLDelight DriverFactory (needs a Context) wired into Koin here.
    implementation(projects.core.data)
    // Same reason: the Android file store needs a Context to copy picked files into
    // private storage, so it is constructed in the Koin bootstrap here.
    implementation(projects.core.platform)
    implementation(projects.observability.logging)
    // Analytics — HAnalytics.init + consent gate configured here.
    implementation(projects.observability.analytics)
    // APM — cold-start span started here, ended from MainActivity on first frame.
    implementation(projects.observability.performance)
    // Crash reporting — CrashReporter.init needs the app's own crash directory.
    implementation(projects.observability.crashreporting)

    implementation(libs.androidx.activity.compose)

    // Compose UI test rule + semantics matchers, and the empty manifest the rule needs
    // to host an activity. Compose Multiplatform ships its own JUnit4 artifact; using
    // androidx's would pull a second, differently-versioned compose ui-test.
    // The flow's semantics tags, shared with the UI so a typo can't split them.
    androidTestImplementation(projects.feature.onboarding)
    androidTestImplementation(projects.feature.servicelog)
    // FairnessTestTags — the fairness report is asserted by identity, not by its copy.
    androidTestImplementation(projects.feature.fairnessCheck)
    // The vault's semantics tags, shared with the UI so a typo can't split them.
    androidTestImplementation(projects.feature.documentVault)
    // The garage's semantics tags, for the same reason.
    androidTestImplementation(projects.feature.garage)
    // The cost tracker's semantics tags, and its SpendCategory-keyed row tags.
    androidTestImplementation(projects.feature.costTracker)
    // The health score's semantics tags, and its HealthFactorKind-keyed row tags.
    androidTestImplementation(projects.feature.healthscore)
    // The timeline's semantics tags, its id-keyed row tags, and the ActivityCategory the
    // filter sheet's rows are keyed by.
    androidTestImplementation(projects.feature.timeline)
    // Home's semantics tags — the dashboard is the one feature the app already depends on
    // for the shell, but the tags live behind its own module boundary all the same.
    androidTestImplementation(projects.feature.dashboard)
    // The profile's test tags — its settings rows and the delete confirmation, which the
    // end-to-end suite drives.
    androidTestImplementation(projects.feature.profile)
    // Auth's two field tags. Neither field has any text to aim at.
    androidTestImplementation(projects.feature.auth)
    // SmsCodeReader and SecureStore — the auth suite puts a fake in front of both, so a
    // sign-in never waits on a real SMS or leaves a session behind for the next test.
    androidTestImplementation(projects.core.platform)
    // Reaching the driver to reset tables between end-to-end runs.
    androidTestImplementation(projects.core.data)
    // The ServiceLogRepository port, to drive a delete the way the app writes one.
    androidTestImplementation(projects.core.domain)
    androidTestImplementation(libs.sqldelight.runtime)
    androidTestImplementation(libs.koin.core)
    androidTestImplementation(libs.compose.uiTestJunit4)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose's ui-test-junit4 drags in Espresso 3.5.0, which calls
    // InputManager.getInstance() — removed in current Android, so every test dies in
    // Espresso.onIdle before it reaches an assertion. Declared explicitly to win.
    androidTestImplementation(libs.androidx.espresso.core)
    // Stubs the system document picker for the vault's upload flow: the picker is another
    // app's activity, so the only way to test "the owner picked a file" is to answer the
    // intent with a result.
    androidTestImplementation(libs.androidx.espresso.intents)
    debugImplementation(libs.androidx.compose.uiTestManifest)
    // Compose tooling preview (annotations + debug renderer) is supplied by the
    // odo.compose.multiplatform convention plugin.
}
