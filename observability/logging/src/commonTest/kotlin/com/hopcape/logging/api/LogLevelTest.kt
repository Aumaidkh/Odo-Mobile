package com.hopcape.logging.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogLevelTest {

    @Test
    fun priorities_areStrictlyAscending() {
        val order = listOf(
            LogLevel.VERBOSE, LogLevel.DEBUG, LogLevel.INFO,
            LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL,
        )
        val priorities = order.map { it.priority }
        assertEquals(priorities.sorted(), priorities, "declaration order must match priority order")
        assertEquals(priorities.distinct(), priorities, "priorities must be unique")
    }

    @Test
    fun priorities_haveExpectedValues() {
        assertEquals(0, LogLevel.VERBOSE.priority)
        assertEquals(5, LogLevel.FATAL.priority)
    }

    @Test
    fun higherLevel_hasHigherPriority() {
        assertTrue(LogLevel.ERROR.priority > LogLevel.WARN.priority)
        assertTrue(LogLevel.WARN.priority > LogLevel.INFO.priority)
    }
}
