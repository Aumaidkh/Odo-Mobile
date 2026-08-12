package com.hopcape.logging.internal

import com.hopcape.logging.RecordingLogFileStore
import com.hopcape.logging.api.FileLoggingConfig
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.LoggerConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class LoggerFactoryTest {

    @Test
    fun noOpLogger_acceptsEveryCall_withoutThrowing() {
        val logger = LoggerFactory.createNoOpLogger()

        // The pre-init fallback: everything is a silent no-op, never a crash.
        logger.verbose("T", "v")
        logger.info("T", "i", fields = mapOf("email" to "a@b.com"))
        logger.log(LogLevel.FATAL, "T", "fatal")
        logger.flush()
    }

    @Test
    fun create_withFileLogging_sealsAnyOrphanFileBeforeWritingAnything() {
        // "A new file every cold start" (docs/LOGGING_PLAN.md §6.3) isn't a per-event
        // RotationPolicy — it's this seal, done once, before the new session's FileSink
        // exists at all (RotationPolicy's own doc explains why).
        val store = RecordingLogFileStore()
        val config = LoggerConfig(
            environment = LoggerConfig.Environment.DEBUG,
            fileLogging = FileLoggingConfig(store = store),
        )

        LoggerFactory.create(config)

        assertEquals(1, store.sealOrphansCallCount)
    }

    @Test
    fun create_withoutFileLogging_neverTouchesAStore_andBuildsAWorkingLogger() {
        val config = LoggerConfig(environment = LoggerConfig.Environment.PRODUCTION)
        val logger = LoggerFactory.create(config)

        // Nothing to assert on a store — there isn't one. Just confirm the Logcat-only
        // logger this must fall back to still works.
        logger.info("T", "e")
        logger.flush()
    }

    @Test
    fun create_withFilePathOnlyAndNoFileLogging_stillBuildsAWorkingLogger() {
        // filePath alone can no longer build a real file sink (§3 of the plan) — it never
        // actually wrote real bytes before this either. Only confirming it doesn't throw.
        val config = LoggerConfig(environment = LoggerConfig.Environment.DEBUG, filePath = "app_logs.log")
        val logger = LoggerFactory.create(config)

        logger.warn("T", "e")
        logger.flush()
    }
}
