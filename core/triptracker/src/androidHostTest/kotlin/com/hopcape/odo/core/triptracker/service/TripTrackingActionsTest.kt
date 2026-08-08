package com.hopcape.odo.core.triptracker.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [routeNotificationAction]'s pure mapping — the notification's Pause/Resume/Not-driving
 * `PendingIntent`s all carry one of these action strings, and this is the only part of
 * that routing that doesn't need a real Android `Intent`/`Service` to verify.
 */
class TripTrackingActionsTest {

    @Test
    fun pauseAction_routesToPause() {
        assertEquals(TripTrackerAction.PAUSE, routeNotificationAction(TripTrackingActions.ACTION_PAUSE))
    }

    @Test
    fun resumeAction_routesToResume() {
        assertEquals(TripTrackerAction.RESUME, routeNotificationAction(TripTrackingActions.ACTION_RESUME))
    }

    @Test
    fun discardAction_routesToDiscard() {
        assertEquals(TripTrackerAction.DISCARD, routeNotificationAction(TripTrackingActions.ACTION_DISCARD))
    }

    @Test
    fun nullAction_routesToNothing() {
        // The plain intent TripForegroundSession.start() sends to (re-)enter the
        // foreground state has no action at all — must not be mistaken for a command.
        assertNull(routeNotificationAction(null))
    }

    @Test
    fun unknownAction_routesToNothing() {
        assertNull(routeNotificationAction("some.other.action"))
    }
}
