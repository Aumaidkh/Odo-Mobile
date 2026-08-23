package com.hopcape.odo

import com.hopcape.logging.api.HLogger
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
 * **The video variant is not built.** `onboarding_video_enabled` can be switched on, in the
 * console or from the QA screen, and the usual flow still runs, because a remote flag can
 * only reach code the installed APK already contains. That would be an invisible no-op, so
 * this says so in the logs instead. When the video flow ships, the `videoEnabled` branch
 * returns its destination and this comment goes away.
 */
internal fun onboardingStartDestination(
    returning: Boolean,
    config: OnboardingConfig,
): OdoDestination {
    if (returning) return OdoDestination.Home
    if (config.videoEnabled) {
        HLogger.tag(TAG).w("onboarding_video_not_built")
    }
    return OdoDestination.Welcome
}

private const val TAG = "Onboarding"
