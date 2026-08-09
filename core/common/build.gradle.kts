import com.codingfeline.buildkonfig.compiler.FieldSpec

plugins {
    // Pure utilities module (Either re-export, ID generation, Clock).
    // No Compose, no domain — it sits at the very bottom of the stack so
    // any module may depend on it. All KMP/Android/iOS boilerplate comes from the
    // odo.kmp.library convention plugin; only identity + deps live here.
    alias(libs.plugins.odo.kmpLibrary)
    // Koin, for the one module that publishes the shared IdGenerator + Clock.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
    // Generates the one BuildKonfig.BUILD_TYPE every module reads — see the buildkonfig {}
    // block below. Lives here, not per-consumer, because :core:common is the one module
    // every other module can already depend on ("sits at the very bottom of the stack").
    alias(libs.plugins.buildkonfig)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.core.common"
    }

    sourceSets {
        commonMain.dependencies {
            // Arrow is `api` so every dependent (e.g. :core:domain) gets
            // Either / EitherNel through this single dependency — boundary error
            // handling is a shared concern, not re-declared per module.
            api(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Picks the BuildKonfig flavor when nothing was passed explicitly (`-Pbuildkonfig.flavor=…`).
// A *heuristic*, not a guarantee: KMP androidLibrary modules (com.android.kotlin.multiplatform
// .library, what every module here uses) have no debug/release build types of their own to
// read from directly — only :androidApp, a classic com.android.application module, has a
// real one. This infers intent from the requested task names — "assembleDebug",
// "installDebug", "testAndroidHostTest", … name their own build type; anything else
// (a release build, or a task this heuristic doesn't recognise) falls to "release", the
// conservative default. A build that genuinely wants "debug" for something the heuristic
// doesn't catch must ask for it explicitly with -Pbuildkonfig.flavor=debug.
if (!project.hasProperty("buildkonfig.flavor")) {
    val looksLikeDebugBuild = gradle.startParameter.taskNames.any { it.contains("Debug") }
    project.extensions.extraProperties.set("buildkonfig.flavor", if (looksLikeDebugBuild) "debug" else "release")
}

buildkonfig {
    packageName = "com.hopcape.odo.core.common"
    // internal by default (Minimal public surface) would make this invisible to every
    // other module — the entire point here is that any module can read it.
    exposeObjectWithName = "BuildKonfig"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "BUILD_TYPE", "release")
        // Same `odo-versionName`/`odo-versionCode` catalog entries the app module's
        // versionName/versionCode come from (AndroidApplicationConventionPlugin) — one
        // source of truth, just baked into every module instead of only :androidApp.
        buildConfigField(FieldSpec.Type.STRING, "VERSION_NAME", libs.versions.odo.versionName.get())
        buildConfigField(FieldSpec.Type.LONG, "VERSION_CODE", libs.versions.odo.versionCode.get())
    }
    defaultConfigs("debug") {
        buildConfigField(FieldSpec.Type.STRING, "BUILD_TYPE", "debug")
    }
}
