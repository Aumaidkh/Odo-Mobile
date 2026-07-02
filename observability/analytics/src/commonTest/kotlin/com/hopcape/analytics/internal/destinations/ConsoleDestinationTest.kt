package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.testEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleDestinationTest {

    @Test
    fun track_echoesEventNameAndProperties_toSink() {
        val lines = mutableListOf<String>()
        ConsoleDestination(sink = { lines += it })
            .track(testEvent("bill_scanned", properties = mapOf("odometer" to 42_000)))

        val line = lines.single()
        assertTrue(line.contains("bill_scanned"))
        assertTrue(line.contains("odometer"))
    }

    @Test
    fun identify_echoesUserId_toSink() {
        val lines = mutableListOf<String>()
        ConsoleDestination(sink = { lines += it }).identify(UserTraits("user-1"))

        assertTrue(lines.single().contains("user-1"))
    }

    @Test
    fun name_isConsole() {
        assertEquals("console", ConsoleDestination().name)
    }
}
