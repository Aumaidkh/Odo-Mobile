package com.hopcape.performance.internal.export

import com.hopcape.performance.internal.model.CompletedSpan
import com.hopcape.performance.testSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafeSpanExporterTest {

    private class Boom(override val name: String = "boom") : SpanExporter {
        override fun export(span: CompletedSpan): Unit = throw RuntimeException("kaboom")
        override fun flush(): Unit = throw RuntimeException("flush kaboom")
    }

    @Test
    fun export_reportsThenRethrows_soDispatcherCanRetry() {
        var reported: Pair<String, String?>? = null
        val safe = SafeSpanExporter(Boom()) { name, error -> reported = name to error.message }

        assertFailsWith<RuntimeException> { safe.export(testSpan("op", "1")) }
        assertEquals("boom", reported?.first)
        assertTrue(reported?.second?.contains("kaboom") == true)
    }

    @Test
    fun flush_reportsButSwallows() {
        var reported = false
        val safe = SafeSpanExporter(Boom()) { _, _ -> reported = true }

        safe.flush() // must not throw
        assertTrue(reported)
    }
}
