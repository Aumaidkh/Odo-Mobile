package com.hopcape.odo.feature.onboarding.presentation.makewelcome

import androidx.compose.runtime.Immutable

/**
 * The v2 Welcome, where picking a make is the first tap of setup. The v1 pitch in
 * `welcome/` stays for the kill switch and is deleted in 1.4.6.
 */
@Immutable
internal data class MakeWelcomeUiState(
    /** The remote title, or null to show the bundled one. */
    val title: String? = null,
    val tiles: List<String> = emptyList(),
    val allMakes: List<String> = emptyList(),
    val otherOpen: Boolean = false,
)

internal sealed interface MakeWelcomeEvent {
    data class MakeTapped(val make: String) : MakeWelcomeEvent
    data object OtherTapped : MakeWelcomeEvent
    data object OtherDismissed : MakeWelcomeEvent

    /** A make picked or typed in the "Other" sheet. */
    data class OtherPicked(val make: String) : MakeWelcomeEvent
    data object SignInClicked : MakeWelcomeEvent
    data object TermsClicked : MakeWelcomeEvent
    data object PrivacyClicked : MakeWelcomeEvent
}

internal sealed interface MakeWelcomeEffect {
    data class OpenSetup(val make: String) : MakeWelcomeEffect
    data object OpenSignIn : MakeWelcomeEffect
    data object OpenTerms : MakeWelcomeEffect
    data object OpenPrivacy : MakeWelcomeEffect
}
