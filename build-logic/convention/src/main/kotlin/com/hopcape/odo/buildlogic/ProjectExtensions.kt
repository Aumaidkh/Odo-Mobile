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
