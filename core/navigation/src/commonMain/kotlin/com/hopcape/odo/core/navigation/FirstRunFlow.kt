package com.hopcape.odo.core.navigation

/**
 * True when [destination] is a step of the first-run flow.
 *
 * The flow is one of the two intros — [OdoDestination.Welcome] or its video variant
 * [OdoDestination.WelcomeVideo] — then the [OdoDestination.Onboarding] car step, then the
 * value screen that pays for it. Which intro opened the flow depends on a remote flag, and
 * the setup screen does not know which one it was; a set named here covers both.
 *
 * Only the first-run arrival at [OdoDestination.CarValue] counts. The same destination is
 * reachable from the garage, and popping *that* one as part of finishing a flow it was never
 * in would take the owner somewhere they never came from.
 *
 * Pass it to [NavigationCommand.FinishFlow] when first run completes: every step is
 * popped, including the intro at the root of the stack, so back from wherever the owner
 * lands can never replay the first run (issue #352).
 */
fun isFirstRunStep(destination: OdoDestination): Boolean = when (destination) {
    OdoDestination.Welcome,
    OdoDestination.WelcomeVideo,
    OdoDestination.Onboarding,
    -> true

    is OdoDestination.CarValue -> destination.firstRun

    else -> false
}
