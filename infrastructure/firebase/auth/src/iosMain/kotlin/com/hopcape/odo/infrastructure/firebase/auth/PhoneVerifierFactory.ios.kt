package com.hopcape.odo.infrastructure.firebase.auth

import com.hopcape.odo.core.domain.auth.PhoneVerifier

// v1.0 is Android-only (see CLAUDE.md). Firebase phone auth on iOS needs an APNs auth key
// uploaded to the console, silent-push handling in the AppDelegate and a reCAPTCHA fallback
// for devices that cannot receive one — none of which this repo has. Until then iOS reports
// that no code can be sent rather than sending the owner to a code screen for nothing.
internal actual fun createPhoneVerifier(onDiagnostic: (String) -> Unit): PhoneVerifier =
    UnavailablePhoneVerifier(onDiagnostic)
