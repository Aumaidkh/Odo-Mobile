package com.hopcape.logging.internal

import com.hopcape.logging.RecordingSink
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.TraceContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoggerImplTest {

    @Test
    fun fansOutEveryEventToAllSinks() {
        val a = RecordingSink()
        val b = RecordingSink()
        val logger = LoggerImpl(listOf(a, b))

        logger.info("T", "hello")

        assertEquals(1, a.written.size)
        assertEquals(1, b.written.size)
    }

    @Test
    fun buildsEventFromArguments() {
        val sink = RecordingSink()
        LoggerImpl(listOf(sink)).log(
            level = LogLevel.WARN,
            tag = "Auth",
            event = "login",
            traceContext = TraceContext(sessionId = "s1", flowId = "f1", traceId = "t1"),
            fields = mapOf("k" to 1),
        )

        val e = sink.written.single()
        assertEquals(LogLevel.WARN, e.level)
        assertEquals("Auth", e.tag)
        assertEquals("login", e.event)
        assertEquals("s1", e.sessionId)
        assertEquals("f1", e.flowId)
        assertEquals("t1", e.traceId)
        assertEquals(mapOf("k" to 1), e.fields)
    }

    @Test
    fun nullTraceContext_leavesCorrelationIdsNull() {
        val sink = RecordingSink()
        LoggerImpl(listOf(sink)).info("T", "e")

        val e = sink.written.single()
        assertNull(e.sessionId)
        assertNull(e.flowId)
        assertNull(e.traceId)
    }

    @Test
    fun levelSugar_mapsToCorrectLevels() {
        val sink = RecordingSink()
        val logger = LoggerImpl(listOf(sink))

        logger.verbose("T", "v")
        logger.debug("T", "d")
        logger.info("T", "i")
        logger.warn("T", "w")
        logger.error("T", "e")

        assertEquals(
            listOf(LogLevel.VERBOSE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            sink.written.map { it.level },
        )
    }

    @Test
    fun flush_isForwardedToEverySink() {
        val a = RecordingSink()
        val b = RecordingSink()
        LoggerImpl(listOf(a, b)).flush()
        assertEquals(1, a.flushCount)
        assertEquals(1, b.flushCount)
    }
}
