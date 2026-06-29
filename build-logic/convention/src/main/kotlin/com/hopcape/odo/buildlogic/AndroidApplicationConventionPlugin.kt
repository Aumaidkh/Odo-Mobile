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
 * is product-wide (SDK levels, version name/code, JVM target, packaging) so the
 * app module's own build script only declares its identity (namespace +
 * applicationId) and its dependencies.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.intVersion("android-compileSdk")

            defaultConfig {
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

            buildTypes {
                getByName("release") { it.isMinifyEnabled = false }
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
