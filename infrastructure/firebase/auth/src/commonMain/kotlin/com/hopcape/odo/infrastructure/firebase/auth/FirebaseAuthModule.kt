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
 * which trades a verified number for a real Supabase session.
 *
 * **This module binds the unavailable verifier, and that is deliberate.** Sending an SMS
 * needs an `Activity`, which only the Android bootstrap can supply, so the real one is bound
 * by `firebaseAuthAndroidModule` from the platform module — last in `initKoin`, so it wins.
 * iOS keeps what is bound here. A target that has not implemented phone verification refuses
 * out loud rather than silently succeeding; see [UnavailablePhoneVerifier].
 *
 * Both bindings are `single`: the verifier holds the in-flight verification's handle between
 * `startVerification` and `submitCode` (see [PhoneVerifier]), so a new instance per call
 * would lose it and every typed code would look expired.
 */
val firebaseAuthModule = module {
    single<PhoneVerifier> {
        val logger = get<Logger>()
        // The same "a vendor SDK failure is visible in logs, never a silent no-op and never
        // a throw" contract every other Firebase gateway in this repo holds.
        UnavailablePhoneVerifier(onDiagnostic = { message -> logger.warn(TAG, message) })
    }
}

internal const val TAG = "PhoneAuth"
