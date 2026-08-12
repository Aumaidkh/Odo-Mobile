package com.hopcape.odo.core.platform.app

/**
 * What build of Odo this is.
 *
 * A port rather than a generated constant, because the answer comes from the platform's own
 * package metadata and common code cannot read it. The profile shows [versionName]; a
 * support ticket that quotes a version nobody shipped is worse than one quoting none.
 */
interface AppInfo {

    /** The user-facing version, e.g. `1.4.0`. */
    val versionName: String

    /**
     * The monotonically increasing build number (Android `versionCode`, iOS
     * `CFBundleVersion`), used to compare this build against a remote minimum. `0` when it
     * cannot be read, which never satisfies a real minimum and so never blocks anything.
     */
    val versionCode: Long
}
