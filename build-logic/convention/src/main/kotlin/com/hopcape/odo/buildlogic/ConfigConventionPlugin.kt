package com.hopcape.odo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Everything a module needs to declare config with `@ConfigGroup`, in one line.
 *
 * Wiring KSP into a KMP module by hand means four separate things, and copy-pasting them
 * into every module that declares a key is four chances to get one wrong — with failures
 * that surface in unrelated-looking tasks. This plugin owns all four:
 *
 *  - applies the KSP plugin and puts the processor on `kspCommonMainMetadata`;
 *  - adds the generated directory as a `commonMain` source directory;
 *  - adds `:core:config` (the annotations and the runtime the generated code calls);
 *  - orders every task that reads the generated sources after the task that writes them.
 *
 * It also applies `odo.koin`, because each group generates a Koin module. Applying it
 * twice is harmless, so a module that already had it is unaffected.
 *
 * **Only `kspCommonMainMetadata` is used.** The processor runs once, on the common
 * metadata compilation, and every target compiles the one result. Per-target KSP
 * configurations do work on this AGP version — `kspAndroid` resolves, contrary to
 * google/ksp#2476 — but config is a platform-independent declaration and generating it
 * once is the point.
 */
class ConfigConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("odo.koin")

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            // Registered after KSP's own reaction to the same plugin, so its
            // configurations already exist by the time this runs.
            dependencies.add(PROCESSOR_CONFIGURATION, project(PROCESSOR))

            extensions.configure(KotlinMultiplatformExtension::class.java) { kmp ->
                val commonMain = kmp.sourceSets.getByName("commonMain")
                // KSP writes here and nothing puts it on the compilation on its own.
                commonMain.kotlin.srcDir(layout.buildDirectory.dir(GENERATED_SOURCES))
                // api, not implementation: a consumer injecting a generated config
                // interface reads properties whose types come from this module.
                // Guarded so :core:config can declare its own keys without depending
                // on itself.
                if (path != RUNTIME) {
                    commonMain.dependencies { api(project(RUNTIME)) }
                }
            }
        }

        // The generated directory is a commonMain srcDir, so every compilation inherits
        // it as an input while only the metadata task produces it. Gradle fails the build
        // on an undeclared implicit dependency like that.
        //
        // "Every consumer" is wider than the compile tasks: applying KSP also creates a
        // ksp task per compilation (kspKotlinIosArm64 and friends). Those have no
        // processor on their configuration and generate nothing, but they still read the
        // directory. Missing them fails the build in a task that looks unrelated to
        // config, which is the single strongest reason this plugin exists.
        // The task is taken as a parameter rather than a receiver on purpose: inside
        // `with(target)` a receiver-style lambda resolves `this` and `name` against the
        // Project, so the type check silently never matches and the ordering is never
        // applied.
        tasks.configureEach { task ->
            val readsGeneratedSources =
                task is KotlinCompilationTask<*> || task.name.startsWith("ksp")
            if (readsGeneratedSources && task.name != GENERATOR_TASK) {
                task.dependsOn(GENERATOR_TASK)
            }
        }
    }

    private companion object {
        const val RUNTIME = ":core:config"
        const val PROCESSOR = ":core:config:processor"
        const val PROCESSOR_CONFIGURATION = "kspCommonMainMetadata"
        const val GENERATOR_TASK = "kspCommonMainKotlinMetadata"
        const val GENERATED_SOURCES = "generated/ksp/metadata/commonMain/kotlin"
    }
}
