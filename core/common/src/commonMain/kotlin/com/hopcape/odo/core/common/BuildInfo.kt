package com.hopcape.odo.core.common

/**
 * What build of Odo this is, decided at compile time by Gradle.
 *
 * Every field comes from [BuildKonfig], generated per module from the `odo-*` version
 * catalog entries and the debug/stage/release flavor (`core/common/build.gradle.kts`) —
 * the same numbers Gradle bakes into the APK, not something read from the OS at runtime.
 * `:core:platform`'s `AppInfo` reads the installed package instead and needs a Context;
 * this is for code that wants an answer before Koin is even up, or that cannot take a
 * platform port at all (e.g. picking a debug-only interval).
 *
 * The two agree by construction on Android: the suffixes below are the same catalog
 * entries `AndroidApplicationConventionPlugin` applies to the APK.
 *
 * Public, not `internal`, on purpose: every module already sits above `:core:common`, and
 * the point of this object is that any of them can read it directly.
 */
object BuildInfo {

    /** The build variant this code was compiled as. */
    val variant: BuildVariant = when (BuildKonfig.BUILD_TYPE) {
        "debug" -> BuildVariant.DEBUG
        "stage" -> BuildVariant.STAGE
        else -> BuildVariant.RELEASE
    }

    /**
     * The version without the build type's suffix, e.g. `1.0.0-beta01`. This is the
     * release the build came from; [versionName] is what was installed.
     */
    val baseVersionName: String = BuildKonfig.VERSION_NAME

    /** What this build type adds to the version, e.g. `-stage`. Empty on release. */
    val versionNameSuffix: String = BuildKonfig.VERSION_NAME_SUFFIX

    /**
     * The user-facing version this build actually carries, e.g. `1.0.0-beta01-stage` —
     * the same string the package manager reports for the installed APK.
     */
    val versionName: String = baseVersionName + versionNameSuffix

    /** The monotonically increasing build number, e.g. `1`. Shared by all three types. */
    val versionCode: Long = BuildKonfig.VERSION_CODE

    /** The base application ID, without the build type's suffix: `com.hopcape.odo`. */
    val baseApplicationId: String = BuildKonfig.APPLICATION_ID

    /** What this build type adds to the application ID, e.g. `.stage`. Empty on release. */
    val applicationIdSuffix: String = BuildKonfig.APPLICATION_ID_SUFFIX

    /**
     * The application ID this build installs under, e.g. `com.hopcape.odo.stage`. All three
     * build types can sit on one device precisely because this differs between them.
     */
    val applicationId: String = baseApplicationId + applicationIdSuffix

    /**
     * Version and build number in one line, e.g. `1.0.0-beta01-stage (1)`. What a tester
     * or a bug report should quote — the version name alone does not say which build.
     */
    val displayVersion: String = "$versionName ($versionCode)"

    /** Shorthand for `variant == BuildVariant.DEBUG` — the common case at a call site. */
    val isDebug: Boolean get() = variant == BuildVariant.DEBUG

    /** Shorthand for `variant == BuildVariant.STAGE`. */
    val isStage: Boolean get() = variant == BuildVariant.STAGE

    /** Shorthand for `variant == BuildVariant.RELEASE` — the build that reaches the store. */
    val isRelease: Boolean get() = variant == BuildVariant.RELEASE

    /**
     * Whether performance spans should be sent to a real vendor backend (Firebase
     * Performance). Everything except debug: a build under active development shouldn't
     * need a vendor console open to see whether tracing itself works, but a stage build
     * is meant to behave like production, and its traces are the last chance to notice a
     * regression before the store gets it. Stage reports under its own applicationId, so
     * it does not mix into production's numbers.
     */
    val isPerformanceReportingEnabled: Boolean get() = variant != BuildVariant.DEBUG
}

/** The three build variants Odo ships — see [BuildInfo.variant]. */
enum class BuildVariant {
    DEBUG,

    /** A production-shaped build for QA. Not debuggable, signed with the debug key. */
    STAGE,

    RELEASE,
}
