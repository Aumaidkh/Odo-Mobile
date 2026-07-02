package com.hopcape.odo.feature.onboarding.presentation.welcome

/**
 * The intro carousel's MVI contract — kept in one file because the whole slice is
 * trivial (a page index + a "done" signal). State-in / events-out: the UI reports
 * what the user did; [WelcomeViewModel] decides whether that advances the pager or
 * finishes the intro.
 */

/** User intents the intro carousel dispatches to [WelcomeViewModel]. */
internal sealed interface WelcomeEvent {

    /**
     * The pager settled on [index] (swipe or programmatic scroll). Keeps state in
     * sync with a horizontally-swipeable pager without inferring direction.
     */
    data class PageChanged(val index: Int) : WelcomeEvent

    /**
     * Primary CTA ("Continue", or "Get Started" on the last slide). Advances to the
     * next slide, or finishes the carousel when already on the last one.
     */
    data object NextClicked : WelcomeEvent

    /** Go back one slide (clamped at the first); optional in-carousel affordance. */
    data object BackClicked : WelcomeEvent

    /** "Skip" — leave the intro immediately and go straight to car setup. */
    data object SkipClicked : WelcomeEvent
}

/**
 * One-shot side effects the carousel emits exactly once (vs. the durable render
 * state). Collected by the route host; never re-applied on recomposition.
 */
internal sealed interface WelcomeEffect {

    /**
     * The intro is done (finished on the last slide or skipped): route the user into
     * car setup. The route host acts on this — presentation only decides.
     */
    data object NavigateToOnboarding : WelcomeEffect
}
