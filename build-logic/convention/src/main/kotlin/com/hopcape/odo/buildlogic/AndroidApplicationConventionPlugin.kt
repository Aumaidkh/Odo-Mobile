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

                getByName("release") { it.isMinifyEnabled = false }
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
