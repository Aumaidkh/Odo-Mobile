package com.hopcape.logging.api

import com.hopcape.logging.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals

class ScopedLoggerTest {

    private val scopeTrace = TraceContext(sessionId = "sess")

    private fun scope(delegate: RecordingLogger) =
        ScopedLogger(delegate, tag = "SCOPE", traceContext = scopeTrace)

    @Test
    fun sugar_bindsScopeTag_andMapsLevels() {
        val rec = RecordingLogger()
        val log = scope(rec)

        log.d("d"); log.i("i"); log.w("w"); log.e("e")

        assertEquals(
            listOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            rec.entries.map { it.level },
        )
        assertEquals(List(4) { "SCOPE" }, rec.entries.map { it.tag })
    }

    @Test
    fun passesFieldsThrough() {
        val rec = RecordingLogger()
        scope(rec).i("evt", mapOf("k" to 1))
        assertEquals(mapOf("k" to 1), rec.last.fields)
    }

    @Test
    fun usesScopeTraceContext_whenCallerGivesNone() {
        val rec = RecordingLogger()
        scope(rec).d("evt")
        assertEquals(scopeTrace, rec.last.traceContext)
    }

    @Test
    fun explicitTraceContext_takesPrecedenceOverScope() {
        val rec = RecordingLogger()
        val explicit = TraceContext(sessionId = "sess", traceId = "override")

        scope(rec).log(LogLevel.INFO, tag = "OTHER", event = "evt", traceContext = explicit)

        assertEquals(explicit, rec.last.traceContext)
        assertEquals("OTHER", rec.last.tag)
    }

    @Test
    fun withTrace_derivesChildScope_carryingNewTraceId() {
        val rec = RecordingLogger()

        scope(rec).withTrace("attempt-1").i("evt")

        assertEquals("sess", rec.last.traceContext?.sessionId)
        assertEquals("attempt-1", rec.last.traceContext?.traceId)
    }

    @Test
    fun flush_isForwardedToDelegate() {
        val rec = RecordingLogger()
        scope(rec).flush()
        assertEquals(1, rec.flushCount)
    }
}
