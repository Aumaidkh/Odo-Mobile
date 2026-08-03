package com.hopcape.odo.infrastructure.supabase.http

import com.hopcape.odo.infrastructure.supabase.SupabaseEnvironment

/**
 * The bearer token to send on a Supabase request.
 *
 * A seam, not a feature: today every call goes out as the anon role, and RLS policies scoped
 * to `auth.uid()` will reject anything owner-scoped. When Supabase phone auth lands (M5) the
 * session's JWT is returned from here and every adapter starts acting as the signed-in owner
 * with no call site changing.
 */
internal fun interface SupabaseAccessTokens {

    /** The current access token, or null to fall back to the project's anon key. */
    suspend fun current(): String?
}

/**
 * Sends the anon key as the bearer, which is what Supabase expects from an unauthenticated
 * client.
 *
 * This is the honest state before auth exists. It deliberately does not invent a token: an
 * owner-scoped read returning nothing under RLS is a clear signal that sign-in is missing,
 * whereas a fabricated token would fail in a way nobody can read.
 */
internal class AnonAccessTokens(private val environment: SupabaseEnvironment) : SupabaseAccessTokens {
    override suspend fun current(): String = environment.anonKey
}
