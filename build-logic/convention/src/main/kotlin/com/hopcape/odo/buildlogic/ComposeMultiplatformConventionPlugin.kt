package com.hopcape.odo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applies the Compose Multiplatform + Compose compiler plugins. Kept as its own
 * convention so both the shared library and the Android app opt into Compose the
 * same way, without re-listing the two plugin ids in every module.
 *
 * For KMP modules it also ships the Compose UI surface every screen needs
 * (runtime/foundation/ui/material3/resources) plus the lifecycle ViewModel +
 * `collectAsStateWithLifecycle` artifacts, into `commonMain`. This is registered
 * via `withPlugin` so it only fires for multiplatform modules — the Android
 * application module has no `commonMain` and is intentionally left untouched.
 * Module-specific extras (tooling preview, navigation, etc.) still live in the
 * owning module's build file.
 */
class ComposeMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.configure(KotlinMultiplatformExtension::class.java) { kmp ->
                kmp.sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.findLibrary("compose-runtime").get())
                    implementation(libs.findLibrary("compose-foundation").get())
                    implementation(libs.findLibrary("compose-ui").get())
                    implementation(libs.findLibrary("compose-material3").get())
                    implementation(libs.findLibrary("compose-components-resources").get())
                    implementation(libs.findLibrary("androidx-lifecycle-viewmodelCompose").get())
                    implementation(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
                }
            }
        }
    }
}
