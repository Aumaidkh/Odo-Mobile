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

    /** "Already using Odo? Sign in" — for an owner whose records are already on the server. */
    data object SignInClicked : WelcomeEvent

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

    /**
     * Sign in before setting anything up, for an owner who has done this before.
     *
     * Signing out or reinstalling clears the local rows, so a returning owner reaching this
     * screen has an empty app and a full server. Sync brings back the car, the logs and the
     * documents; retyping the car in setup would instead create a second one.
     */
    data object OpenSignIn : WelcomeEffect

    data object OpenTerms : WelcomeEffect

    data object OpenPrivacy : WelcomeEffect
}
