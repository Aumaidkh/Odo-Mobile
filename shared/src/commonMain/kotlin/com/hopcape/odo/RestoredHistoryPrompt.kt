package com.hopcape.odo

import com.hopcape.odo.core.navigation.OdoDestination

/**
 * Whether to tell the owner that signing in brought their car's history back (issue #425).
 *
 * A plain function next to [shouldPromptPendingFills], for the same reason: the rule is
 * worth testing on its own, and a composable is a bad place to keep one.
 *
 * **Only on a top-level tab**, like the fuel-fill prompt: the restore lands during a
 * background sync, which can finish while the owner is halfway through scanning a bill, and
 * a sheet over that is an interruption rather than a welcome.
 *
 * Unlike that prompt this is not "once per launch". The marker is cleared when the sheet is
 * answered, not when it is shown, so a sheet the owner swiped away comes back on the next
 * tab they land on. It is the one moment that explains where a garage full of unfamiliar
 * records came from, and a swipe is not an answer to that.
 */
internal fun shouldAnnounceRestoredHistory(
    currentDestination: OdoDestination?,
    hasRestoredHistory: Boolean,
): Boolean {
    if (!hasRestoredHistory) return false
    return currentDestination is OdoDestination.TopLevel
}
