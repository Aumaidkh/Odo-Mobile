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
}
