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
 * via `withPlugin` so it only fires for multiplatform modules.
 *
 * Compose **tooling preview** is bundled here as well, so any module that opts
 * into Compose can write `@Preview`s without re-declaring the dependency:
 *  - `compose.ui:ui-tooling-preview` (the `@Preview` annotations) is compiled
 *    into `commonMain` (KMP) / `implementation` (Android app);
 *  - `compose.ui:ui-tooling` (the Android preview *renderer*) is placed on the
 *    Android runtime classpath only — never compiled against.
 */
class ComposeMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }

        // KMP modules: the Compose UI surface + `@Preview` annotations go into
        // commonMain so every screen and every preview compiles everywhere.
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
                    // @Preview / @PreviewLightDark and friends — every Compose
                    // module gets these, so multipreviews (@OdoThemePreviews) work
                    // without any per-module dependency declaration.
                    implementation(libs.findLibrary("compose-uiToolingPreview").get())
                }
            }
        }

        // KMP Android library: the preview *renderer* only needs to be present on
        // the Android runtime classpath (Studio uses it to render @Previews).
        pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            dependencies.addProvider(
                "androidRuntimeClasspath",
                libs.findLibrary("compose-uiTooling").get(),
            )
        }

        // Android application module (no commonMain): the preview annotations for
        // compilation, and the renderer on the debug runtime classpath.
        pluginManager.withPlugin("com.android.application") {
            dependencies.addProvider(
                "implementation",
                libs.findLibrary("compose-uiToolingPreview").get(),
            )
            dependencies.addProvider(
                "debugImplementation",
                libs.findLibrary("compose-uiTooling").get(),
            )
        }
    }
}
