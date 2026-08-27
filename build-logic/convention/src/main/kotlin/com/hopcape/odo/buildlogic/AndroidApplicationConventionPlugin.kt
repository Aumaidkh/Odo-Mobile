package com.hopcape.odo.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Convention for the Android application module. Owns all the build config that
 * is product-wide (applicationId, SDK levels, version name/code, build types, JVM
 * target, packaging) so the app module's own build script only declares its
 * namespace and its dependencies.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.intVersion("android-compileSdk")

            defaultConfig {
                applicationId = libs.version("odo-applicationId")
                minSdk = libs.intVersion("android-minSdk")
                targetSdk = libs.intVersion("android-targetSdk")
                versionCode = libs.intVersion("odo-versionCode")
                versionName = libs.version("odo-versionName")
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }

            packaging {
                resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }

            // Needed for `BuildConfig.DEBUG` (AGP 8+ no longer generates it by
            // default). The app reads it to pick the debug vs production Logger.
            buildFeatures {
                buildConfig = true
            }

            // The upload key, when this machine has one. Declared before buildTypes so the
            // release type below can pick it up. Absent — a fresh clone, or CI without the
            // secrets — leaves the config uncreated and the release APK unsigned, which is
            // enough for the compile gate CI actually runs.
            val uploadSigning = readUploadSigningMaterial()
            if (uploadSigning != null) {
                signingConfigs.create("upload") {
                    it.storeFile = uploadSigning.storeFile
                    it.storePassword = uploadSigning.storePassword
                    it.keyAlias = uploadSigning.keyAlias
                    it.keyPassword = uploadSigning.keyPassword
                    // Signing scheme versions are left to AGP, which picks them from minSdk.
                    // At 26 that is v2 alone: v1 (JAR signing) only matters below API 24, and
                    // Play re-signs the artifact with its own key anyway.
                }
            }

            // Three build types, all installable side by side because each carries its own
            // applicationId suffix. The same suffixes are baked into BuildInfo from the
            // version catalog (core/common/build.gradle.kts), so what the app reports about
            // itself and what Gradle actually built always agree.
            //
            // A suffixed applicationId needs its own Firebase client: google-services.json
            // must list com.hopcape.odo.debug and com.hopcape.odo.stage alongside the base
            // ID, or the Google Services plugin fails the build for those two types. CI has
            // no google-services.json at all (the plugin is only applied when the file
            // exists), so this only affects a local checkout.
            buildTypes {
                getByName("debug") {
                    it.applicationIdSuffix = libs.version("odo-debugApplicationIdSuffix")
                    it.versionNameSuffix = libs.version("odo-debugVersionNameSuffix")
                }

                // A production-shaped build for QA: not debuggable, no debug-only tooling,
                // but signed with the debug key so it can be installed without the release
                // keystore, and unminified so a stack trace from a tester is readable.
                create("stage") {
                    it.applicationIdSuffix = libs.version("odo-stageApplicationIdSuffix")
                    it.versionNameSuffix = libs.version("odo-stageVersionNameSuffix")
                    it.isDebuggable = false
                    it.isMinifyEnabled = false
                    it.signingConfig = signingConfigs.getByName("debug")
                    // Dependencies (AARs, and any library module that does have build types)
                    // publish debug and release only. Without this, resolving the stage
                    // variant fails with "no matching variant" on the first such dependency.
                    it.matchingFallbacks.add("release")
                }

                // Release's R8 configuration — minified, resources shrunk, the same keep
                // rules — but debuggable and signed with the debug key, so a crash that only
                // happens once R8 has run can be reproduced under a debugger without the
                // upload keystore. `:androidApp:assembleMinified`.
                //
                // Named "minified" rather than anything containing debug/stage/release, and
                // that is not cosmetic: resolvedBuildFlavor() infers the BuildKonfig flavor by
                // looking for those three words in the requested task names, so
                // `assembleReleaseDebug` would match two at once and fail the build outright.
                // An unrecognised name falls to its conservative default, "release", which is
                // what this variant wants anyway — BuildInfo reports release and
                // :infrastructure:supabase points at the production project, which is the
                // whole point of reproducing a production crash here.
                //
                // No applicationIdSuffix on purpose. google-services.json lists only
                // com.hopcape.odo, and a suffixed variant needs its own Firebase client or
                // the Google Services plugin fails the build for it. Sharing release's
                // applicationId means this cannot sit alongside a store build on the same
                // device; the versionNameSuffix is what tells them apart on screen.
                create("minified") {
                    it.isDebuggable = true
                    it.isMinifyEnabled = true
                    it.isShrinkResources = true
                    it.proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    it.versionNameSuffix = "-minified"
                    it.signingConfig = signingConfigs.getByName("debug")
                    it.matchingFallbacks.add("release")
                }

                // R8 in full mode (AGP's default), plus resource shrinking. The keep rules
                // live in androidApp/proguard-rules.pro; `proguard-android-optimize.txt` is
                // the platform's own baseline, which the stack's libraries extend through
                // the consumer rules they ship.
                //
                // Stage stays unminified on purpose, so R8 first runs on the build that goes
                // to the store. That is a deliberate trade — see the build types table in
                // README.md — and it is why a release APK is worth smoke-testing on a device
                // before it is uploaded, not just built.
                getByName("release") {
                    it.isMinifyEnabled = true
                    it.isShrinkResources = true
                    it.proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    if (uploadSigning != null) {
                        it.signingConfig = signingConfigs.getByName("upload")
                    }
                    // Asks AGP to put a native symbol file in the bundle, which is what Play
                    // wants when it warns "this App Bundle contains native code, and you've
                    // not uploaded debug symbols".
                    //
                    // It produces nothing today, and the warning stays. Odo has no C/C++ of
                    // its own; all six .so files come from prebuilt dependencies (ML Kit OCR,
                    // CameraX, androidx graphics and datastore) and every one of them is
                    // already stripped when it arrives from the AAR, so there is no debug
                    // info left to extract. Checked at both levels — FULL and SYMBOL_TABLE
                    // each leave the output directory empty, and the bundle gets no
                    // BUNDLE-METADATA symbol entry either way. Nothing in the build can
                    // change that; the symbols would have to come from Google.
                    //
                    // Kept because the day this app does ship native code, symbol upload is
                    // already wired and nobody has to remember it.
                    it.ndk.debugSymbolLevel = "FULL"
                }
            }

            // Lint runs from this module on CI. The app module is a thin shell,
            // so `checkDependencies` is what makes the run mean anything — it
            // pulls in every :core/:feature module the app depends on. Today's
            // findings are frozen in the baseline; only new ones fail a build,
            // so the gate is useful from day one without a cleanup sprint.
            lint {
                checkDependencies = true
                baseline = target.file("lint-baseline.xml")
                abortOnError = true
                warningsAsErrors = false
                htmlReport = true
                xmlReport = true
            }
        }

        // AGP 9 ships built-in Kotlin support; align the Kotlin JVM target with
        // the Java one above so they never drift.
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(libs.version("jvmTarget")))
            }
        }
    }
}
