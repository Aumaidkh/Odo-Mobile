package com.hopcape.odo.feature.onboarding.presentation.welcome

/**
 * The Welcome pitch's MVI contract — events and effects only, **no UI state**.
 *
 * That absence is deliberate rather than an omission: the screen renders constants and its
 * own entrance animation, so there is nothing for a state holder to hold. What it does need
 * a [WelcomeViewModel] for is the decisions and the bookkeeping around the pitch — where the
 * CTA goes, and (as they land) the "onboarding started" analytics and the first-run marker.
 */

/** What the owner did on the pitch. */
internal sealed interface WelcomeEvent {
    /** "Continue with mobile" — into car setup. */
    data object ContinueClicked : WelcomeEvent

    data object TermsClicked : WelcomeEvent

    data object PrivacyClicked : WelcomeEvent
}

/** One-shot handoffs, performed by the route host. */
internal sealed interface WelcomeEffect {
    /**
     * Go straight into car setup — no sign-in first. Odo is offline-first, so first run has
     * to reach a working car without an account; auth is offered *after* setup instead.
     */
    data object OpenCarSetup : WelcomeEffect

    data object OpenTerms : WelcomeEffect

    data object OpenPrivacy : WelcomeEffect
}
