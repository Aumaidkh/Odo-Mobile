package com.hopcape.odo

import com.hopcape.odo.core.navigation.OdoDestination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the unanswered-detections sheet is allowed to interrupt.
 *
 * The rule matters more than it looks: this is the one screen in the app that opens itself.
 * Getting it wrong in either direction is bad — never offering means the detections the owner
 * missed are lost anyway, and offering too eagerly turns a recovery into a nag.
 */
class PendingFillPromptTest {

    @Test
    fun offersOnATopLevelTabWhenSomethingIsWaiting() {
        assertTrue(
            shouldPromptPendingFills(
                currentDestination = OdoDestination.Home,
                pendingCount = 2,
                alreadyPrompted = false,
            ),
        )
    }

    @Test
    fun neverInterruptsSomethingTheOwnerIsInTheMiddleOf() {
        // Reviewing a scanned bill, editing a car, confirming another fill: all worse moments
        // to ask about yesterday's payment than simply waiting.
        assertFalse(
            shouldPromptPendingFills(
                currentDestination = OdoDestination.BillScanner.Review("photo"),
                pendingCount = 2,
                alreadyPrompted = false,
            ),
        )
    }

    @Test
    fun asksOnceALaunch_soLaterMeansLater() {
        assertFalse(
            shouldPromptPendingFills(
                currentDestination = OdoDestination.Home,
                pendingCount = 2,
                alreadyPrompted = true,
            ),
        )
    }

    @Test
    fun saysNothingWhenThereIsNothingToSay() {
        assertFalse(
            shouldPromptPendingFills(
                currentDestination = OdoDestination.Home,
                pendingCount = 0,
                alreadyPrompted = false,
            ),
        )
    }

    @Test
    fun waitsWhileTheDestinationIsStillResolving() {
        assertFalse(
            shouldPromptPendingFills(
                currentDestination = null,
                pendingCount = 3,
                alreadyPrompted = false,
            ),
        )
    }
}
