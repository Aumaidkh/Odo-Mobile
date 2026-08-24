package com.hopcape.odo

import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.onboarding.OnboardingConfig

/**
 * Where the app opens: Home for a returning owner, otherwise whichever onboarding the
 * config selects.
 *
 * A function rather than an expression inside the composable, for the same reason
 * [shouldRedirectToTripLogged] is one — the guard stays unit-testable without a Compose
 * tree.
 *
 * **The flag switches the first page, not the flow.** Both intros lead to the same car
 * setup, and both are only ever shown to an install that has not completed onboarding. The
 * video variant's clips are streamed, so a device with no network sees its copy without the
 * video rather than being sent somewhere else — that fallback lives in the screen, not
 * here, because "no clip" is a rendering state and not a routing decision.
 */
internal fun onboardingStartDestination(
    returning: Boolean,
    config: OnboardingConfig,
): OdoDestination = when {
    returning -> OdoDestination.Home
    config.videoEnabled -> OdoDestination.WelcomeVideo
    else -> OdoDestination.Welcome
}
