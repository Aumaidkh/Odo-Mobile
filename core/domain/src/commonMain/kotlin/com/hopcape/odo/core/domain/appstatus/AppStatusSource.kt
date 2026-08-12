package com.hopcape.odo.core.domain.appstatus

/**
 * Port over wherever [AppStatus] actually lives (Firebase Remote Config in production).
 *
 * One method, because fetching a snapshot is its only job — every caller wants a lambda in
 * tests, hence `fun interface`.
 */
fun interface AppStatusSource {

    /**
     * One snapshot, or `null` when it could not be read (no network, unconfigured build,
     * SDK error). Never throws — a source that cannot answer is a source that says so,
     * not one that crashes the caller.
     */
    suspend fun fetch(): AppStatus?
}
