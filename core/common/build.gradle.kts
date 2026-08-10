import com.codingfeline.buildkonfig.compiler.FieldSpec
import com.hopcape.odo.buildlogic.resolvedBuildFlavor

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
//
// The inference itself lives in `resolvedBuildFlavor()` (build-logic), because a second caller
// now needs the same answer: :infrastructure:supabase picks its project by build type, and a
// release APK that reported itself as debug while talking to the production database — or the
// reverse — is precisely the disagreement one shared function rules out.
//
// One flavor is generated for the whole invocation, so asking for two build types at once
// ("assembleDebug assembleRelease") has no correct answer — one of the two APKs would carry
// BuildInfo for the other. resolvedBuildFlavor() fails on that rather than shipping it: a
// release APK that reports itself as debug turns off performance reporting and shortens the
// Remote Config fetch interval in production.
if (!project.hasProperty("buildkonfig.flavor")) {
    project.extensions.extraProperties.set("buildkonfig.flavor", resolvedBuildFlavor())
}

buildkonfig {
    packageName = "com.hopcape.odo.core.common"
    // internal by default (Minimal public surface) would make this invisible to every
    // other module — the entire point here is that any module can read it.
    exposeObjectWithName = "BuildKonfig"
    // Release is the base config; the debug and stage blocks below override only what
    // differs, exactly as the build types do in AndroidApplicationConventionPlugin.
    // Both read the same catalog entries, so BuildInfo can never describe a build that
    // Gradle did not produce.
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "BUILD_TYPE", "release")
        buildConfigField(FieldSpec.Type.STRING, "APPLICATION_ID", libs.versions.odo.applicationId.get())
        // Release adds nothing to either. An empty string, not a null: a suffix that is
        // absent is still a suffix, and callers concatenate it unconditionally.
        buildConfigField(FieldSpec.Type.STRING, "APPLICATION_ID_SUFFIX", "")
        buildConfigField(FieldSpec.Type.STRING, "VERSION_NAME", libs.versions.odo.versionName.get())
        buildConfigField(FieldSpec.Type.STRING, "VERSION_NAME_SUFFIX", "")
        buildConfigField(FieldSpec.Type.LONG, "VERSION_CODE", libs.versions.odo.versionCode.get())
    }
    defaultConfigs("debug") {
        buildConfigField(FieldSpec.Type.STRING, "BUILD_TYPE", "debug")
        buildConfigField(FieldSpec.Type.STRING, "APPLICATION_ID_SUFFIX", libs.versions.odo.debugApplicationIdSuffix.get())
        buildConfigField(FieldSpec.Type.STRING, "VERSION_NAME_SUFFIX", libs.versions.odo.debugVersionNameSuffix.get())
    }
    defaultConfigs("stage") {
        buildConfigField(FieldSpec.Type.STRING, "BUILD_TYPE", "stage")
        buildConfigField(FieldSpec.Type.STRING, "APPLICATION_ID_SUFFIX", libs.versions.odo.stageApplicationIdSuffix.get())
        buildConfigField(FieldSpec.Type.STRING, "VERSION_NAME_SUFFIX", libs.versions.odo.stageVersionNameSuffix.get())
    }
}
