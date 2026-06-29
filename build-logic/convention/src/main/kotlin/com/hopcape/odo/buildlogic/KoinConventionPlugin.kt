package com.hopcape.odo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Sets up Koin dependency injection for whichever module applies it. The wiring
 * is registered through `withPlugin` so this convention can sit in any order in
 * a module's `plugins { }` block and still react once the relevant base plugin
 * is applied:
 *
 *  - KMP module  -> `koin-core` in commonMain, `koin-test` in commonTest.
 *  - Android app -> `koin-android` (Application-level startKoin + androidContext).
 *
 * Koin *modules* and `startKoin {}` themselves stay in the owning module's code;
 * this plugin only provides the dependencies.
 */
class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.configure(KotlinMultiplatformExtension::class.java) { kmp ->
                kmp.sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.findLibrary("koin-core").get())
                }
                kmp.sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.findLibrary("koin-test").get())
                }
            }
        }

        pluginManager.withPlugin("com.android.application") {
            dependencies.addProvider(
                "implementation",
                libs.findLibrary("koin-android").get(),
            )
        }
    }
}
