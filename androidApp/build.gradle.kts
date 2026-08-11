plugins {
    alias(libs.plugins.odo.androidApplication)
    alias(libs.plugins.odo.composeMultiplatform)
    // Application-level startKoin + androidContext (adds koin-android).
    alias(libs.plugins.odo.koin)
    // Reads google-services.json (not committed — see .gitignore) and registers
    // the app with Firebase at build time. Declared here with `apply false` and
    // applied conditionally below — unlike Supabase's local.properties, this plugin
    // hard-fails the whole build the moment it's applied without the file, and CI /
    // a fresh checkout never has it.
    alias(libs.plugins.google.services) apply false
    // Injects the build ID resource FirebaseCrashlytics.init() reads at startup.
    // Without it applied, the crashlytics runtime SDK on the classpath (pulled in by
    // :infrastructure:firebase:crashlytics) still eagerly initializes via Firebase's
    // own ContentProvider and crashes app launch with "The Crashlytics build ID is
    // missing" — before any app code, including CrashConfig.isDebug/onDiagnostic,
    // ever runs. Same conditional as google-services below: no config file, no plugin.
    alias(libs.plugins.firebase.crashlytics) apply false
    // Bytecode-instruments app-start/network/screen-render traces automatically —
    // custom spans (FirebasePerformanceSink) work without it. Same conditional as
    // google-services/firebase-crashlytics: no config file, no plugin.
    alias(libs.plugins.firebase.perf) apply false
}

// google-services.json identifies a specific Firebase project, so it's gitignored and
// only present for whoever has dropped their own copy in locally. Applying the plugin
// unconditionally breaks every PR check on a clean checkout (processDebugGoogleServices
// fails outright, taking the whole :androidApp task graph with it) — CI has no file to
// give it and never should. Skipping the plugin here mirrors the runtime posture
// OdoApplication.configureAnalytics/configureFirebaseForIos already have: no config
// means FirebaseAnalyticsSink is simply left out of destinations, not a crash.
//
// A local google-services.json has to list all three application IDs — com.hopcape.odo,
// com.hopcape.odo.debug and com.hopcape.odo.stage — because each build type installs
// under its own. Register the two extra Android apps in the Firebase console and
// download the file again; without them the build fails with "No matching client found
// for package name 'com.hopcape.odo.debug'" before anything is compiled.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
    apply(plugin = libs.plugins.firebase.perf.get().pluginId)
}

android {
    // Only the Kotlin/resource package lives here; applicationId, SDK levels,
    // version name/code, JVM target, packaging and the debug/stage/release build
    // types all come from the odo.android.application convention plugin, which
    // reads them from the version catalog.
    namespace = "com.hopcape.odo"

    defaultConfig {
        // On-device (instrumented) tests. The end-to-end onboarding test drives the real
        // app — real Koin graph, real SQLite, real navigation — so it has to run here
        // rather than on the JVM.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.shared)
    // The Android SQLDelight DriverFactory (needs a Context) wired into Koin here.
    implementation(projects.infrastructure.database)
    implementation(projects.core.data)
    // AppStatusProvider — resolved via Koin on every foreground (docs/APP_STATUS_PLAN.md §5.3).
    implementation(projects.core.domain)
    // Same reason: the Android file store needs a Context to copy picked files into
    // private storage, so it is constructed in the Koin bootstrap here.
    implementation(projects.core.platform)
    // tripTrackerAndroidModule is included from the Koin bootstrap here, same as
    // corePlatformAndroidModule above.
    implementation(projects.core.triptracker)
    implementation(projects.observability.logging)
    // Analytics — HAnalytics.init + consent gate configured here.
    implementation(projects.observability.analytics)
    // FirebaseAnalyticsSink is constructed directly here (see AnalyticsSink KDoc):
    // HAnalytics.init runs before the Koin graph starts.
    implementation(projects.infrastructure.firebase.analytics)
    // ProcessLifecycleOwner — flushes the analytics queue on every foreground.
    implementation(libs.androidx.lifecycle.process)
    // APM — cold-start span started here, ended from MainActivity on first frame.
    implementation(projects.observability.performance)
    // FirebasePerformanceSink is constructed directly in configureApm (see
    // FirebasePerformanceSink KDoc): APM.init runs before initKoin.
    implementation(projects.infrastructure.firebase.performance)
    // Crash reporting — CrashReporter.init needs the app's own crash directory.
    implementation(projects.observability.crashreporting)
    // FirebaseCrashlyticsSink is constructed directly here (see CrashSink KDoc):
    // CrashReporter.init runs before the Koin graph starts.
    implementation(projects.infrastructure.firebase.crashlytics)
    // firebaseAuthAndroidModule — the real PhoneVerifier, bound from the platform module
    // because verifyPhoneNumber needs an Activity and only this bootstrap can supply one.
    implementation(projects.infrastructure.firebase.auth)

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
    // The reminders test tags — the FAB, the per-preset "Remind me" buttons and the
    // settings switches, none of which carry text of their own.
    androidTestImplementation(projects.feature.reminders)
    // The scanner's field tags, and the scan/payment ports the suite puts a fake in front
    // of — the extractor has no implementation yet, so without one there is no readable
    // bill to drive at all.
    androidTestImplementation(projects.feature.billscanner)
    androidTestImplementation(projects.core.navigation)
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
    // GrantPermissionRule — the scanner's flows split on whether the camera is allowed.
    androidTestImplementation(libs.androidx.test.rules)
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
