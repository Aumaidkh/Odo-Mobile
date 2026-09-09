package com.hopcape.odo

import com.hopcape.odo.core.navigation.OdoDestination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the "your history came back" sheet is allowed to open itself (issue #425).
 *
 * The restore lands during a background sync, so the moment it finishes is not a moment the
 * owner chose. This is the rule that decides whether it waits.
 */
class RestoredHistoryPromptTest {

    @Test
    fun announcesOnATopLevelTab() {
        assertTrue(
            shouldAnnounceRestoredHistory(
                currentDestination = OdoDestination.Home,
                hasRestoredHistory = true,
            ),
        )
    }

    @Test
    fun neverInterruptsSomethingTheOwnerIsInTheMiddleOf() {
        // A sync finishing while a bill is being reviewed is not a reason to cover it.
        assertFalse(
            shouldAnnounceRestoredHistory(
                currentDestination = OdoDestination.BillScanner.Review("photo"),
                hasRestoredHistory = true,
            ),
        )
    }

    @Test
    fun saysNothingWhenNothingWasRestored() {
        assertFalse(
            shouldAnnounceRestoredHistory(
                currentDestination = OdoDestination.Home,
                hasRestoredHistory = false,
            ),
        )
    }

    @Test
    fun waitsWhileTheDestinationIsStillResolving() {
        assertFalse(
            shouldAnnounceRestoredHistory(
                currentDestination = null,
                hasRestoredHistory = true,
            ),
        )
    }
}
