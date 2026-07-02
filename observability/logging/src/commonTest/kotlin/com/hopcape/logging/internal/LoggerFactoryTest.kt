package com.hopcape.logging.internal

import com.hopcape.logging.api.LogLevel
import kotlin.test.Test

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
}
