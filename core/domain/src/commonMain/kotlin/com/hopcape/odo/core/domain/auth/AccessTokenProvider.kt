package com.hopcape.odo.core.domain.auth

/**
 * The bearer token for an authenticated call, or null when nobody is signed in.
 *
 * This port is what keeps the dependency arrows straight. Every PostgREST and Storage
 * request needs the current token, and the thing that holds it is the session manager in
 * `:feature:auth` — so without a port in the middle, `:infrastructure:supabase` would have
 * to depend on a feature, and dependencies only ever point inward.
 *
 * `suspend`, because answering may mean refreshing first. A caller asking for a token gets
 * a usable one or null; it never gets an expired one back to discover the hard way.
 *
 * Null is an ordinary answer, not a failure. Odo works fully offline, and a request made
 * without a session simply falls back to the anonymous key and is refused by row-level
 * security — which is the correct outcome, not an error to propagate.
 */
fun interface AccessTokenProvider {

    /** A token good to send right now, or null if there is no session. */
    suspend fun currentAccessToken(): String?
}
