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
    /** Logo URL by make name; a make without one shows its monogram. */
    val logos: Map<String, String> = emptyMap(),
    val otherOpen: Boolean = false,
    /** An unfinished setup to offer, which replaces the grid (S1r). Null shows the grid. */
    val resume: ResumeDraft? = null,
    /** False until the draft has been read, so the grid never flashes before S1r. */
    val ready: Boolean = false,
)

/** The car named so far in an unfinished setup. */
@Immutable
internal data class ResumeDraft(val make: String, val model: String?)

internal sealed interface MakeWelcomeEvent {
    data class MakeTapped(val make: String) : MakeWelcomeEvent
    data object OtherTapped : MakeWelcomeEvent
    data object OtherDismissed : MakeWelcomeEvent

    /** A make picked or typed in the "Other" sheet. */
    data class OtherPicked(val make: String) : MakeWelcomeEvent
    data object ContinueSetupClicked : MakeWelcomeEvent
    data object PickDifferentCarClicked : MakeWelcomeEvent
    data object SignInClicked : MakeWelcomeEvent
    data object TermsClicked : MakeWelcomeEvent
    data object PrivacyClicked : MakeWelcomeEvent
}

internal sealed interface MakeWelcomeEffect {
    data class OpenSetup(val make: String, val resume: Boolean = false) : MakeWelcomeEffect
    data object OpenSignIn : MakeWelcomeEffect
    data object OpenTerms : MakeWelcomeEffect
    data object OpenPrivacy : MakeWelcomeEffect
}
