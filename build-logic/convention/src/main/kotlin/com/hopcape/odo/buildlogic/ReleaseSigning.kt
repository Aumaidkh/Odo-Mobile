package com.hopcape.odo.buildlogic

import java.io.File
import java.util.Properties
import org.gradle.api.Project

/**
 * The upload key material, once all four parts have been found.
 *
 * "Upload", not "release": the app uses Play App Signing, so Google holds the key the
 * installed APK is signed with and this one only proves an upload came from us. Losing it
 * is recoverable — Play can reset an upload key — which is why it is generated locally and
 * kept out of the repo rather than treated as unreplaceable.
 */
internal data class UploadSigningMaterial(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/**
 * Reads the upload key from `local.properties`, falling back to the environment.
 *
 * Same order and the same reason as the Supabase config in
 * `infrastructure/supabase/build.gradle.kts`: a developer keeps their values in a gitignored
 * file, and CI has no such file so it supplies them as secrets instead. Reading happens at
 * configuration time into plain values, which is what keeps the configuration cache working.
 *
 * Returns `null` when anything is missing or the keystore is not where it says it is. A
 * checkout with no key still has to build a release APK — it just comes out unsigned, the
 * same posture `google-services.json` and Supabase already take. Failing here would break
 * CI and every fresh clone for the sake of a build almost nobody runs.
 */
internal fun Project.readUploadSigningMaterial(): UploadSigningMaterial? {
    val localProperties = rootProject.file("local.properties")
    val properties = Properties().apply {
        if (localProperties.exists()) localProperties.inputStream().use(::load)
    }

    fun value(property: String, environment: String): String? =
        (properties.getProperty(property) ?: System.getenv(environment))?.takeIf { it.isNotBlank() }

    val storePath = value("odo.upload.storeFile", "ODO_UPLOAD_STORE_FILE") ?: return null
    val storePassword = value("odo.upload.storePassword", "ODO_UPLOAD_STORE_PASSWORD") ?: return null
    val keyAlias = value("odo.upload.keyAlias", "ODO_UPLOAD_KEY_ALIAS") ?: return null
    // Keystores are usually created with one password for both. Falling back keeps the
    // common case to three entries instead of four.
    val keyPassword = value("odo.upload.keyPassword", "ODO_UPLOAD_KEY_PASSWORD") ?: storePassword

    // A relative path is relative to the repo root, not to :androidApp — that is where
    // local.properties itself lives, so it is what someone writing the path will mean.
    val storeFile = File(storePath).takeIf { it.isAbsolute } ?: rootProject.file(storePath)
    if (!storeFile.exists()) {
        logger.warn("Upload keystore not found at ${storeFile.absolutePath}; release builds will be unsigned.")
        return null
    }

    return UploadSigningMaterial(storeFile, storePassword, keyAlias, keyPassword)
}
