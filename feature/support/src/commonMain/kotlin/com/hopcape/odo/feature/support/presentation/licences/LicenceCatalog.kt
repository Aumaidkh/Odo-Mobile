package com.hopcape.odo.feature.support.presentation.licences

/**
 * A licence Odo's dependencies are used under, and where its full text is published.
 *
 * [name] and [url] are not translated. A licence is identified by its exact name, and
 * "Apache License 2.0" is that name in every language — a localised variant would name a
 * document that does not exist.
 */
internal data class Licence(val name: String, val url: String)

/** One shipped dependency, grouped under the licence it is used under. */
internal data class LicencedLibrary(val name: String, val licence: Licence)

private val APACHE_2 = Licence("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0")
private val MIT = Licence("MIT License", "https://opensource.org/license/mit")
private val ANDROID_SDK = Licence("Android Software Development Kit License", "https://developer.android.com/studio/terms")

/**
 * What Odo ships, and what it is used under.
 *
 * **Maintained by hand, and that is a known cost.** No licence-collection plugin is
 * configured, so a dependency added to `gradle/libs.versions.toml` will not add itself here.
 * Adding one — `app.cash.licensee` or similar — is the real fix and is a build-logic change,
 * not a change to this file.
 *
 * Test-only and build-only dependencies are deliberately absent: JUnit, Espresso,
 * `androidx.test` and the Android Gradle plugin are not in the APK, and attributing code
 * that does not ship claims more than is true.
 *
 * Grouped by licence rather than listed flat, because the grouping is the part that carries
 * legal meaning — the reader wants to know what terms apply, not to read an alphabet.
 */
internal val LICENCED_LIBRARIES: List<LicencedLibrary> = listOf(
    LicencedLibrary("Kotlin and kotlinx", APACHE_2),
    LicencedLibrary("Jetpack Compose and Compose Multiplatform", APACHE_2),
    LicencedLibrary("AndroidX", APACHE_2),
    LicencedLibrary("Navigation 3", APACHE_2),
    LicencedLibrary("AndroidX Lifecycle for Multiplatform", APACHE_2),
    LicencedLibrary("SQLDelight", APACHE_2),
    LicencedLibrary("Koin", APACHE_2),
    LicencedLibrary("Ktor", APACHE_2),
    LicencedLibrary("Arrow", APACHE_2),
    LicencedLibrary("Firebase Android SDK", APACHE_2),
    LicencedLibrary("Firebase Kotlin SDK", APACHE_2),
    LicencedLibrary("RevenueCat Purchases", MIT),
    LicencedLibrary("Google Play services", ANDROID_SDK),
    LicencedLibrary("ML Kit", ANDROID_SDK),
)

/** The libraries under each licence, in the order the licences first appear above. */
internal fun licencedLibrariesByLicence(): Map<Licence, List<String>> =
    LICENCED_LIBRARIES.groupBy({ it.licence }, { it.name })
