package com.hopcape.odo

import com.hopcape.odo.core.config.ConfigRefresher
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.feature.onboarding.OnboardingConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull

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

/**
 * The same decision, but made only after the first Remote Config fetch has had a chance
 * to land.
 *
 * On a fresh install the fetch launched at startup races the database read that gates
 * this decision. The database read usually wins, `videoEnabled` still reads as its
 * compiled default, and the install gets the old onboarding until the next launch
 * (issue #351). So a new install awaits its own [ConfigRefresher.refresh] call — refresh
 * never throws, and a second in-flight fetch is the SDK's problem to coalesce — before
 * reading the flag.
 *
 * The wait is bounded by [firstFetchWait]: a device that cannot reach the backend must
 * not sit on the startup screen, and the compiled default is the designed answer for it.
 * A returning owner goes Home whatever the config says, so only a new install waits.
 *
 * **400ms, measured down from 1.5s.** Tracing put this at 692ms of a 2.9s cold start — the
 * largest thing the gate does, on a new install's first launch. All it buys is which of
 * two intros to open, and both are fine. Raise it only with a measurement.
 */
internal suspend fun onboardingStartDestination(
    returning: Boolean,
    config: OnboardingConfig,
    refresher: ConfigRefresher,
    firstFetchWait: Duration = 400.milliseconds,
): OdoDestination {
    if (!returning) withTimeoutOrNull(firstFetchWait) { refresher.refresh() }
    return onboardingStartDestination(returning, config)
}
