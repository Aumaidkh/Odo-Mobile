package com.hopcape.odo.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The single `libs` version catalog, shared with the main build. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Reads a required version string from the catalog, e.g. `libs.version("jvmTarget")`. */
internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow {
        IllegalStateException("Missing version '$alias' in the version catalog")
    }.requiredVersion

internal fun VersionCatalog.intVersion(alias: String): Int = version(alias).toInt()

/**
 * Which build type this Gradle invocation is producing: `"debug"`, `"stage"` or `"release"`.
 *
 * A *heuristic*, not a guarantee. KMP `androidLibrary` modules — which is every module here
 * except `:androidApp` — have no debug/stage/release build types of their own to read from, so
 * this infers intent from the requested task names: `assembleStage`, `assembleDebug`,
 * `installDebug` and friends name their own build type. Anything unrecognised (a bare `test`, a
 * `lint` run, an IDE sync) falls to `"release"`, the conservative default: it turns nothing
 * debug-only on and points nothing at a development backend.
 *
 * `-Pbuildkonfig.flavor=<type>` overrides the inference and is the escape hatch for anything the
 * task names do not describe. The property is named for its first caller, `:core:common`'s
 * BuildKonfig block, and is deliberately shared: one answer per invocation means `BuildInfo` and
 * the Supabase project a build talks to can never disagree about which build it is.
 *
 * Asking for two build types at once has no correct answer — one artifact would carry the
 * other's configuration — so it fails here rather than shipping. Public, not internal: build
 * scripts call it directly, and they are a separate compilation unit from this one.
 */
fun Project.resolvedBuildFlavor(): String {
    (findProperty("buildkonfig.flavor") as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    val taskNames = gradle.startParameter.taskNames
    val requested = buildList {
        if (taskNames.any { it.contains("Stage") }) add("stage")
        if (taskNames.any { it.contains("Debug") }) add("debug")
        if (taskNames.any { it.contains("Release") }) add("release")
    }
    check(requested.size <= 1) {
        "This build asks for more than one build type at once (${requested.joinToString()}), " +
            "but build-type constants are generated once per invocation. Run the tasks " +
            "separately, or pass -Pbuildkonfig.flavor=<${requested.joinToString("|")}> to choose."
    }
    return requested.singleOrNull() ?: "release"
}

/**
 * iOS framework base name derived from the Gradle path so it is stable and
 * unique per module without per-module configuration:
 *   `:shared` -> "Shared", `:core:domain` -> "CoreDomain".
 * The existing iosApp Xcode project imports `Shared`, which this preserves.
 */
internal val Project.iosFrameworkBaseName: String
    get() = path.removePrefix(":")
        .split(":")
        .joinToString("") { segment ->
            segment.replaceFirstChar { it.uppercaseChar() }
        }
