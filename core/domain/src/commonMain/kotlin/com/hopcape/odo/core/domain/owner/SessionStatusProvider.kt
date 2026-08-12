package com.hopcape.odo.core.domain.owner

/**
 * Port answering "does this device have a signed-in Odo account?".
 *
 * Odo is offline-first: a car can be set up, logged, and costed with no account at
 * all, so signing in is a **prompt, never a gate**. This port is what lets a feature
 * ask whether that prompt is still worth showing without knowing how sessions are
 * stored, or which feature owns them — the same Ports & Adapters shape as
 * [CurrentOwnerProvider].
 *
 * The MVP has no real auth, so the binding is a stub reporting "signed out" — the
 * honest answer while sessions don't exist. When Supabase phone auth lands it
 * implements this same port and every caller stays untouched.
 */
fun interface SessionStatusProvider {
    /** `true` once the owner has verified a number and a session is stored. */
    fun isSignedIn(): Boolean
}
