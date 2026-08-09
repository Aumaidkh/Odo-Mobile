package com.hopcape.odo.core.common

/**
 * What build of Odo this is, decided at compile time by Gradle.
 *
 * Every field comes from [BuildKonfig], generated per module from the `odo-versionName` /
 * `odo-versionCode` version catalog entries and the debug/release flavor
 * (`core/common/build.gradle.kts`) — the same numbers Gradle bakes into the APK, not
 * something read from the OS at runtime. `:core:platform`'s `AppInfo` reads the installed
 * package instead and needs a Context; this is for code that wants an answer before Koin is
 * even up, or that cannot take a platform port at all (e.g. picking a debug-only interval).
 *
 * Public, not `internal`, on purpose: every module already sits above `:core:common`, and
 * the point of this object is that any of them can read it directly.
 */
object BuildInfo {

    /** The build variant this code was compiled as. */
    val variant: BuildVariant = if (BuildKonfig.BUILD_TYPE == "debug") BuildVariant.DEBUG else BuildVariant.RELEASE

    /** The user-facing version, e.g. `1.0`. */
    val versionName: String = BuildKonfig.VERSION_NAME

    /** The monotonically increasing build number, e.g. `1`. */
    val versionCode: Long = BuildKonfig.VERSION_CODE

    /** Shorthand for `variant == BuildVariant.DEBUG` — the common case at a call site. */
    val isDebug: Boolean get() = variant == BuildVariant.DEBUG
}

/** The two build variants Odo ships — see [BuildInfo.variant]. */
enum class BuildVariant {
    DEBUG,
    RELEASE,
}
