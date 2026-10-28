package com.hopcape.odo.core.common.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LifecycleAppSessionTest {

    private var next = 0
    private val session = LifecycleAppSession { "s${next++}" }

    @Test
    fun aProcessStartIsABackgroundSessionOfItsOwn() {
        assertEquals("s0", session.sessionId)
        assertFalse(session.isForeground)
    }

    @Test
    fun comingToTheForegroundStartsANewSession() {
        session.onForeground()
        assertEquals("s1", session.sessionId)
        assertTrue(session.isForeground)
    }

    @Test
    fun goingToTheBackgroundKeepsTheSession() {
        session.onForeground()
        session.onBackground()
        assertEquals("s1", session.sessionId)
        assertFalse(session.isForeground)
    }

    @Test
    fun everyReturnIsANewSession() {
        session.onForeground()
        val first = session.sessionId
        session.onBackground()
        session.onForeground()
        assertNotEquals(first, session.sessionId)
    }
}
