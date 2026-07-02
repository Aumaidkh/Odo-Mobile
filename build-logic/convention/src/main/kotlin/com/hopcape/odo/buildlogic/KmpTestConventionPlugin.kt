package com.hopcape.odo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Wires the baseline multiplatform testing surface into a KMP module's
 * `commonTest`: `kotlin-test` (assertions + the `@Test`/`assert*` API), which is
 * needed by every module that has tests, so it is centralised here instead of
 * being copy-pasted into every build file.
 *
 * The wiring is registered through `withPlugin` so this convention can sit in any
 * order in a module's `plugins { }` block and still react once the Kotlin
 * Multiplatform plugin is applied.
 *
 * Module-specific test dependencies stay in the owning module: e.g.
 * `kotlinx-coroutines-test` (only modules that test suspend code need it), and
 * `koin-test` — the latter is added to `commonTest` by the `odo.koin` convention.
 */
class KmpTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.configure(KotlinMultiplatformExtension::class.java) { kmp ->
                kmp.sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.findLibrary("kotlin-test").get())
                }
            }
        }
    }
}
