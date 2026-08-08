package com.hopcape.logging

import com.hopcape.logging.api.HLogger
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.LoggerConfig
import com.hopcape.logging.internal.model.LogEvent
import com.hopcape.logging.internal.sinks.LogcatSink
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM (android host) integration tests. `LogcatSink` and the `HLogger` facade emit
 * through `println`, so these capture stdout to assert the real end-to-end behaviour:
 * level gating, formatting, PII redaction through the full `LoggerFactory.create`
 * pipeline, and runtime tag-level overrides. `FileSink` writes through a `LogFileStore`
 * instead — see `FileSinkTest` (commonTest) for its coverage.
 *
 * `HLogger` is a process singleton whose `init` is idempotent (first-wins), so
 * every facade test initialises with the same [SHARED_CONFIG] to stay
 * order-independent.
 */
class LoggingOutputIntegrationTest {

    private fun capture(block: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, /* autoFlush = */ true))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }

    private fun event(level: LogLevel, tag: String = "T", ev: String = "e"): LogEvent =
        LogEvent.Builder(tag, ev).level(level).build()

    // ── concrete sinks ──────────────────────────────────────────────

    @Test
    fun logcatSink_emitsFormattedLine_atOrAboveMinLevel() {
        val out = capture {
            LogcatSink(minLevel = LogLevel.INFO).write(event(LogLevel.WARN, tag = "Sync", ev = "pushed batch"))
        }
        assertTrue(out.contains("[WARN]"), out)
        assertTrue(out.contains("Sync"), out)
        assertTrue(out.contains("pushed batch"), out)
    }

    @Test
    fun logcatSink_dropsEventsBelowMinLevel() {
        val out = capture { LogcatSink(minLevel = LogLevel.WARN).write(event(LogLevel.INFO)) }
        assertTrue(out.isBlank(), "sub-threshold event must be dropped, got: $out")
    }

    // ── HLogger facade end-to-end (through LoggerFactory.create) ─────

    @Test
    fun facade_redactsPii_endToEnd() {
        HLogger.init(SHARED_CONFIG)
        val out = capture { HLogger.tag("Auth").i("login", mapOf("email" to "user@odo.app")) }
        assertTrue(out.contains("***email_masked***"), "expected masked email, got: $out")
        assertFalse(out.contains("user@odo.app"), "raw email must never be emitted")
    }

    @Test
    fun facade_runtimeTagOverride_suppressesThenRestores() {
        HLogger.init(SHARED_CONFIG)
        HLogger.setTagLevelOverride("NOISY", LogLevel.ERROR)
        try {
            assertTrue(
                capture { HLogger.tag("NOISY").i("chatter") }.isBlank(),
                "INFO on an ERROR-overridden tag must be suppressed",
            )
            assertTrue(
                capture { HLogger.tag("NOISY").e("real problem") }.contains("real problem"),
                "ERROR still passes the override",
            )
        } finally {
            HLogger.clearTagLevelOverride("NOISY")
        }
        assertTrue(
            capture { HLogger.tag("NOISY").i("back to normal") }.contains("back to normal"),
            "clearing the override restores default gating",
        )
    }

    @Test
    fun facade_stampsSessionContextIntoOutput() {
        HLogger.init(SHARED_CONFIG)
        HLogger.setSession("sess-123")
        val out = capture { HLogger.tag("Home").i("opened") }
        assertTrue(out.contains("sess-123"), "session id should appear in the formatted line: $out")
    }

    private companion object {
        val SHARED_CONFIG = LoggerConfig(
            environment = LoggerConfig.Environment.DEBUG,
            filePath = null, // Logcat-only keeps captured output clean to assert on.
            minLevel = LogLevel.INFO,
            piiRedactionEnabled = true,
        )
    }
}
