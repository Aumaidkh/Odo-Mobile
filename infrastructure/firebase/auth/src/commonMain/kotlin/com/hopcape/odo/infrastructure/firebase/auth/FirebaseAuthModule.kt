package com.hopcape.odo.infrastructure.firebase.auth

import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import org.koin.dsl.module

/**
 * Publishes the [PhoneVerifier] that proves a phone number.
 *
 * Nothing here replaces an earlier binding, unlike the other `:infrastructure:*` modules —
 * `PhoneVerifier` has no stub in `:core:data`, because a verifier nobody can reach is not a
 * useful offline fallback. What consumes it is `:infrastructure:supabase`'s bridge gateway,
 * which trades a verified number for a real Supabase session. So this module must be listed
 * **before** `supabaseModule` in `initKoin`: the bridge resolves this port when it is built.
 *
 * A `single`: the verifier holds the in-flight verification's handle between
 * `startVerification` and `submitCode` (see [PhoneVerifier]), so a new instance per call
 * would lose it and every typed code would look expired.
 */
val firebaseAuthModule = module {
    single<PhoneVerifier> {
        val logger = get<Logger>()
        // The same "a vendor SDK failure is visible in logs, never a silent no-op and never
        // a throw" contract every other Firebase gateway in this repo holds.
        createPhoneVerifier(onDiagnostic = { message -> logger.warn(TAG, message) })
    }
}

private const val TAG = "PhoneAuth"
