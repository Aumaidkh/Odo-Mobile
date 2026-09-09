package com.hopcape.odo.feature.auth.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.navigation.CollectEffects
import com.hopcape.odo.feature.auth.presentation.OtpEffect
import com.hopcape.odo.feature.auth.presentation.OtpViewModel
import com.hopcape.odo.feature.auth.presentation.PhoneEffect
import com.hopcape.odo.feature.auth.presentation.PhoneEvent
import com.hopcape.odo.feature.auth.presentation.PhoneViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.hopcape.odo.core.navigation.FeatureEntryProvider
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.back
import com.hopcape.odo.core.navigation.navigateTo
import com.hopcape.odo.core.platform.sms.SmsCodeStatus
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PhoneRoute(key: OdoDestination.Auth.Phone, navigationManager: NavigationManager) {
    val viewModel = koinViewModel<PhoneViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            // The parsed E.164 number travels, not what was typed — the next screen has to
            // verify against the same thing the code was issued for.
            is PhoneEffect.CodeSent ->
                navigationManager.navigateTo(OdoDestination.Auth.Otp(effect.phone, key.next))

            PhoneEffect.LeaveAuth -> navigationManager.leaveAuth(key)
        }
    }

    // Backing out of a prompt is declining it, so every back runs "Skip for now": the same
    // decision, the same destination, and counted the same way. Going through the ViewModel
    // rather than straight to leaveAuth is what makes the count true — declining is the
    // flow's biggest drop-off, and only the button was reporting it.
    val decline = { viewModel.onEvent(PhoneEvent.SkipClicked) }

    // The system back has to be caught here or it falls through to the host's plain pop,
    // which lands on whatever sits under sign-in and never reaches `next` — the scan or the
    // value screen the owner asked for is dropped, and where auth is the stack root the
    // gesture does nothing at all.
    BackHandler(enabled = true) { decline() }

    PhoneScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = decline,
    )
}

@Composable
internal fun OtpRoute(key: OdoDestination.Auth.Otp, navigationManager: NavigationManager) {
    // The key carries the parsed number, so the ViewModel verifies against exactly what the
    // code was issued for rather than re-parsing a display string.
    val phone = remember(key.phone) { PhoneNumber.of(key.phone).getOrNull() }
    if (phone == null) {
        // Unreachable in the flow — Phone only navigates here with a number it parsed. A
        // hand-built deep link could still get here, and going back is better than a crash.
        LaunchedEffect(Unit) { navigationManager.back() }
        return
    }

    val viewModel = koinViewModel<OtpViewModel> { parametersOf(phone) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OtpEffect.Verified -> navigationManager.navigateTo(OdoDestination.Auth.Verifying(key.next))
            OtpEffect.LeaveAuth -> navigationManager.leaveAuth(key)
            OtpEffect.ChangeNumber -> navigationManager.back()
        }
    }

    OtpScreen(
        state = state,
        onEvent = viewModel::onEvent,
        autoReadStatus = state.autoRead.toCardStatus(),
        onBack = { navigationManager.back() },
        onGetHelp = { /* TODO(auth): open support. */ },
    )
}

@Composable
internal fun VerifyingRoute(key: OdoDestination.Auth.Verifying, navigationManager: NavigationManager) {
    // Reached only after the session already exists — verification happens on the code
    // screen. This is the hand-off beat, not the work.
    VerifyingScreen(
        onDone = { navigationManager.leaveAuth(key) },
    )
}

/**
 * The reader's status as the card's.
 *
 * Unsupported and timed out both collapse to Unavailable: on iOS the keyboard already offers
 * the code, and a lapsed window is not a failure — in both cases the honest thing is to stop
 * claiming to listen and let the owner type it.
 */
private fun SmsCodeStatus.toCardStatus(): AutoReadSmsStatus = when (this) {
    SmsCodeStatus.Listening -> AutoReadSmsStatus.Listening
    is SmsCodeStatus.Received -> AutoReadSmsStatus.Filled
    SmsCodeStatus.Unsupported, SmsCodeStatus.TimedOut -> AutoReadSmsStatus.Unavailable
}
