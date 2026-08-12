package com.hopcape.odo.infrastructure.firebase.auth

import com.google.firebase.auth.FirebaseAuth
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.auth.PhoneVerifier
import com.hopcape.odo.core.domain.auth.VerifiedAccount
import org.koin.dsl.module

/**
 * The real verifier, bound over [firebaseAuthModule]'s unavailable one.
 *
 * Separate from the shared graph for the same reason `corePlatformAndroidModule` is: sending
 * an SMS needs an `Activity`, and that is something common code cannot ask for. The app
 * bootstrap includes this in its platform module, which `initKoin` lists last, so this
 * definition wins.
 *
 * `FirebaseAuth.getInstance()` rather than an injected one — the SDK is initialised by the
 * `google-services` plugin's ContentProvider before any app code runs, the same assumption
 * every other Firebase adapter in this repo already makes.
 */
val firebaseAuthAndroidModule = module {
    single<PhoneVerifier> {
        val logger = get<Logger>()
        FirebasePhoneVerifier(
            auth = FirebaseAuth.getInstance(),
            activities = get(),
            onDiagnostic = { message -> logger.warn(TAG, message) },
        )
    }

    // The account behind that sign-in — read for its number, deleted when the owner erases
    // their account. Bound here rather than in the shared graph so it replaces the
    // unavailable one for the same reason the verifier above does.
    single<VerifiedAccount> {
        val logger = get<Logger>()
        FirebaseVerifiedAccount(
            auth = FirebaseAuth.getInstance(),
            onDiagnostic = { message -> logger.warn(TAG, message) },
        )
    }
}
