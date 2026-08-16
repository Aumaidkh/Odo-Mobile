package com.hopcape.odo

import com.hopcape.odo.core.navigation.OdoDestination

/**
 * Whether to offer the unanswered fuel detections the owner has not dealt with.
 *
 * A plain function next to [shouldRedirectToTripLogged], for the same reason: the rule is
 * worth testing on its own, and a composable is a bad place to keep one.
 *
 * The prompt is a sheet rather than a badge because a detection expires in usefulness. The
 * owner remembers this morning's fill; a row they find three weeks later is one they cannot
 * check the odometer of, and a fill with a guessed odometer is worse than none.
 *
 * **Once per launch, and only on a top-level tab.** Interrupting someone mid-flow — reviewing
 * a bill, editing a car — to ask about a payment from yesterday is a worse trade than waiting
 * until they are idle. And re-asking after they chose "Later" would make the sheet something
 * to dismiss rather than something to read.
 */
internal fun shouldPromptPendingFills(
    currentDestination: OdoDestination?,
    pendingCount: Int,
    alreadyPrompted: Boolean,
): Boolean {
    if (alreadyPrompted) return false
    if (pendingCount <= 0) return false
    return currentDestination is OdoDestination.TopLevel
}
