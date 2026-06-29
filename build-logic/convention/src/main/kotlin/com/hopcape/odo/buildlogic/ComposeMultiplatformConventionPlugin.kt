package com.hopcape.odo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies the Compose Multiplatform + Compose compiler plugins. Kept as its own
 * convention so both the shared library and the Android app opt into Compose the
 * same way, without re-listing the two plugin ids in every module. Compose
 * *dependencies* stay in each module, since they differ per source set.
 */
class ComposeMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }
    }
}
