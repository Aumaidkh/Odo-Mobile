package com.hopcape.analytics.internal.destinations

import com.hopcape.analytics.api.AnalyticsSink
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.testEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SinkDestinationTest {

    private class RecordingSink(override val name: String = "vendor") : AnalyticsSink {
        val tracked = mutableListOf<Triple<String, Map<String, Any?>, Long>>()
        val identified = mutableListOf<UserTraits>()
        var flushCount = 0

        override fun identify(traits: UserTraits) {
            identified += traits
        }

        override fun track(eventName: String, properties: Map<String, Any?>, timestampMs: Long) {
            tracked += Triple(eventName, properties, timestampMs)
        }

        override fun flush() {
            flushCount++
        }
    }

    @Test
    fun name_isDelegated() {
        assertEquals("vendor", SinkDestination(RecordingSink()).name)
    }

    @Test
    fun track_forwardsResolvedFields_notTheInternalEvent() {
        val sink = RecordingSink()
        val destination = SinkDestination(sink)

        destination.track(testEvent("bill_scanned", mapOf("odometer" to 45210)))

        val (name, properties, _) = sink.tracked.single()
        assertEquals("bill_scanned", name)
        assertEquals(45210, properties["odometer"])
    }

    @Test
    fun identify_andFlush_areForwarded() {
        val sink = RecordingSink()
        val destination = SinkDestination(sink)

        destination.identify(UserTraits("u-1"))
        destination.flush()

        assertEquals("u-1", sink.identified.single().userId)
        assertEquals(1, sink.flushCount)
    }

    @Test
    fun aThrowingSink_propagates_soTheCallerMustWrapItInSafeDestination() {
        val destination = SinkDestination(object : AnalyticsSink {
            override val name = "boom"
            override fun identify(traits: UserTraits) = throw IllegalStateException("boom")
            override fun track(eventName: String, properties: Map<String, Any?>, timestampMs: Long) =
                throw IllegalStateException("boom")

            override fun flush() = throw IllegalStateException("boom")
        })

        assertFailsWith<IllegalStateException> { destination.track(testEvent("e")) }
    }
}
