package com.hopcape.odo.feature.auth.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.designsystem.component.OdoPhoneNumberDefaults
import com.hopcape.odo.core.designsystem.component.formatPhoneNumber
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.feature.auth.presentation.AutoReadSmsStatus
import com.hopcape.odo.feature.auth.presentation.OtpScreen
import com.hopcape.odo.feature.auth.presentation.PhoneScreen
import com.hopcape.odo.feature.auth.presentation.VerifyingScreen

/**
 * Auth's contribution to the navigation graph: the [OdoDestination.Auth] sealed group —
 * phone → otp → verifying. Collected by the `:app` host via
 * `getAll<FeatureEntryProvider>()`, so no other module references auth directly.
 *
 * **Entered after car setup, never before it.** Onboarding routes here on completion, and
 * only when `SessionStatusProvider` reports no session — Odo works fully offline, so first
 * run must not stall behind an OTP. Every key carries [OdoDestination.Auth.next]: the
 * surface onboarding would otherwise have gone to itself. Auth simply hands the owner
 * there when it's done, so it never has to know why it was entered.
 *
 * Both exits — verified *and* skipped — leave through [leaveAuth], which pops the whole
 * flow (`popUpTo Phone(next)` inclusive), so back can't return to sign-in either way.
 *
 * The flow is UI-only for now (no real send/verify); the real ViewModel + Supabase phone
 * auth land behind this same key set.
 */
internal class AuthFeatureEntryProvider(
    private val navigationManager: NavigationManager,
) : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Auth.Phone> { key -> PhoneRoute(key, navigationManager) }
        entry<OdoDestination.Auth.Otp> { key -> OtpRoute(key, navigationManager) }
        entry<OdoDestination.Auth.Verifying> { key -> VerifyingRoute(key, navigationManager) }
    }
}

/**
 * Leaves auth for [OdoDestination.Auth.next], clearing every auth key behind it. Used by
 * both the verified path and "Skip for now" — the destination is the same, only the
 * session differs.
 */
private fun NavigationManager.leaveAuth(key: OdoDestination.Auth) {
    navigateTo(
        key.next,
        popUpTo = OdoDestination.Auth.Phone(key.next),
        inclusive = true,
    )
}

@Composable
internal fun PhoneRoute(key: OdoDestination.Auth.Phone, navigationManager: NavigationManager) {
    PhoneScreen(
        // Phone is the root of the stack here — onboarding cleared everything behind it,
        // so there is nothing to pop back to. Backing out of a prompt means declining it,
        // so back and "Skip for now" are the same action; a plain `back()` would be a
        // dead control (Navigator.goBack no-ops at the root).
        onBack = { navigationManager.leaveAuth(key) },
        // TODO(auth): actually request the OTP here. The number rides along on the key so
        //  the OTP screen's "Sent to …" line can state the real one.
        onSendCode = { phone -> navigationManager.navigateTo(OdoDestination.Auth.Otp(phone, key.next)) },
        onSkip = { navigationManager.leaveAuth(key) },
    )
}

@Composable
internal fun OtpRoute(key: OdoDestination.Auth.Otp, navigationManager: NavigationManager) {
    // TODO(auth): source the code, error state, and resend timer from a koinViewModel.
    //  `isError` is a compile-time switch so the wrong-code mockup can be verified live.
    val isError = false
    // TODO(auth): drive from the platform SMS retriever (Android's SMS Retriever API
    //  behind a :core:platform port). Until that exists the card reports Listening — what
    //  the screen looks like in the common case — and never claims a code was auto-filled.
    var autoReadStatus by remember { mutableStateOf(AutoReadSmsStatus.Listening) }

    OtpScreen(
        phone = "${OdoPhoneNumberDefaults.CountryCode} ${formatPhoneNumber(key.phone)}",
        isError = isError,
        autoReadStatus = autoReadStatus,
        onBack = { navigationManager.back() },
        onChange = { navigationManager.back() },
        onResend = { autoReadStatus = AutoReadSmsStatus.Listening },
        onGetHelp = { /* TODO(auth): open support. */ },
        onSkip = { navigationManager.leaveAuth(key) },
        onComplete = { navigationManager.navigateTo(OdoDestination.Auth.Verifying(key.next)) },
    )
}

@Composable
internal fun VerifyingRoute(key: OdoDestination.Auth.Verifying, navigationManager: NavigationManager) {
    VerifyingScreen(
        onDone = { navigationManager.leaveAuth(key) },
    )
}
