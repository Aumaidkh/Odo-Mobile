package com.hopcape.odo.core.domain.shared

/**
 * Turns a public Storage path (`<bucket>/<object>`) into a URL the app can load.
 * Null when the build has no server, so callers fall back to what they show offline.
 */
fun interface PublicAssetUrls {
    fun urlFor(path: String): String?
}
