package com.hopcape.odo.infrastructure.firebase.auth

import com.hopcape.odo.core.domain.auth.PhoneVerifier

// S1 stands the module up; the real Firebase verifier lands in S2, which is where the
// Activity seam it needs is added. Reporting "no code sent" until then keeps the placeholder
// honest — nothing above this can mistake it for a working sign-in.
internal actual fun createPhoneVerifier(onDiagnostic: (String) -> Unit): PhoneVerifier =
    UnavailablePhoneVerifier(onDiagnostic)
