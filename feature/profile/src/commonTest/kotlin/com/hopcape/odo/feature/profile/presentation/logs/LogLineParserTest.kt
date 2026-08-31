package com.hopcape.odo.feature.profile.presentation.logs

import com.hopcape.logging.api.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogLineParserTest {

    /** The exact shape `FileSink.toJsonLine` writes — see `FileSink.kt` in
     *  `:observability:logging`. Extra fields (`sessionId`/`flowId`/`traceId`/custom ones)
     *  are inlined at the top level, not nested, so a real line always carries more keys
     *  than [LogEntry] needs. */
    private fun realLine(
        ts: Long = 1_000L,
        level: String = "INFO",
        tag: String = "Sync",
        event: String = "sync_started",
    ) = """{"ts":$ts,"level":"$level","tag":"$tag","event":"$event","sessionId":"s-1","flowId":null,"traceId":null,"attempt":3}"""

    @Test
    fun parsesAWellFormedLine() {
        val entries = LogLineParser.parseLines(realLine())

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(1_000L, entry.timestampMs)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Sync", entry.tag)
        assertEquals("sync_started", entry.message)
    }

    @Test
    fun parsesEveryLineIndependently_inOrder() {
        val text = realLine(ts = 1L, event = "first") + "\n" + realLine(ts = 2L, event = "second")

        val entries = LogLineParser.parseLines(text)

        assertEquals(listOf("first", "second"), entries.map { it.message })
    }

    @Test
    fun blankLines_areSkipped_notFatal() {
        val text = realLine(event = "a") + "\n\n" + realLine(event = "b")

        assertEquals(listOf("a", "b"), LogLineParser.parseLines(text).map { it.message })
    }

    @Test
    fun aTornWrite_isSkippedRatherThanCrashingTheWholeParse() {
        // Simulates reading the active file mid-append: the last line is an incomplete
        // JSON object because the writer hadn't finished it yet.
        val text = realLine(event = "complete") + "\n" + """{"ts":2,"level":"INFO","tag":"X","ev"""

        assertEquals(listOf("complete"), LogLineParser.parseLines(text).map { it.message })
    }

    @Test
    fun anUnknownLevelName_isSkipped() {
        val text = """{"ts":1,"level":"TRACE","tag":"X","event":"y"}"""

        assertTrue(LogLineParser.parseLines(text).isEmpty())
    }

    @Test
    fun aMissingRequiredField_isSkipped() {
        val missingTag = """{"ts":1,"level":"INFO","event":"y"}"""

        assertTrue(LogLineParser.parseLines(missingTag).isEmpty())
    }

    @Test
    fun emptyInput_producesNoEntries() {
        assertTrue(LogLineParser.parseLines("").isEmpty())
    }
}
