package com.hopcape.odo.feature.onboarding.presentation.contract

/**
 * One-shot side effects the ViewModel emits exactly once (vs. the durable render
 * state). Collected by the UI/app layer; never re-applied on recomposition.
 */
internal sealed interface OnboardingEffect {

    /**
     * Onboarding finished: a [carId] was created and the user should be routed to
     * [destination]. The `:app` nav host acts on this — presentation only decides.
     */
    data class NavigateToStart(
        val destination: StartDestination,
        val carId: String,
    ) : OnboardingEffect

    /**
     * Back was pressed on the first step: onboarding should be popped, returning to
     * whatever preceded it (the intro carousel). The nav host performs the pop;
     * presentation only decides that leaving is warranted.
     */
    data object NavigateBack : OnboardingEffect
}
