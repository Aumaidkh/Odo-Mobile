package com.hopcape.logging.internal.model

import com.hopcape.logging.api.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogEventTest {

    @Test
    fun builder_appliesAllFields() {
        val event = LogEvent.Builder("Auth", "login")
            .level(LogLevel.WARN)
            .sessionId("s1")
            .flowId("f1")
            .traceId("t1")
            .field("k1", 1)
            .fields(mapOf("k2" to "v2"))
            .build()

        assertEquals(LogLevel.WARN, event.level)
        assertEquals("Auth", event.tag)
        assertEquals("login", event.event)
        assertEquals("s1", event.sessionId)
        assertEquals("f1", event.flowId)
        assertEquals("t1", event.traceId)
        assertEquals(mapOf("k1" to 1, "k2" to "v2"), event.fields)
    }

    @Test
    fun builder_hasSensibleDefaults() {
        val event = LogEvent.Builder("T", "e").build()

        assertEquals(LogLevel.INFO, event.level)
        assertNull(event.sessionId)
        assertNull(event.flowId)
        assertNull(event.traceId)
        assertTrue(event.fields.isEmpty())
    }

    @Test
    fun builder_stampsAPositiveTimestamp() {
        val event = LogEvent.Builder("T", "e").build()
        assertTrue(event.timestampMs > 0)
    }

    @Test
    fun builder_snapshotsFields_soLaterMutationsDontLeak() {
        val source = mutableMapOf<String, Any?>("k" to 1)
        val event = LogEvent.Builder("T", "e").fields(source).build()

        source["k"] = 999
        source["added"] = true

        assertEquals(mapOf("k" to 1), event.fields, "event must hold an immutable snapshot")
    }
}
