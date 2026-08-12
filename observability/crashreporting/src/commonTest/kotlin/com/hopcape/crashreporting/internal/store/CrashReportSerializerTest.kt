package com.hopcape.crashreporting.internal.store

import com.hopcape.crashreporting.testReport
import com.hopcape.crashreporting.internal.model.Breadcrumb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrashReportSerializerTest {

    @Test
    fun roundTripsAFullReport() {
        val original = testReport(
            crashId = "crash-xyz",
            isFatal = true,
            breadcrumbs = listOf(
                Breadcrumb(10L, "AUTH", "login_started"),
                Breadcrumb(20L, "NAV", "opened home"),
            ),
            customKeys = mapOf("screen" to "home", "attempts" to 3L, "offline" to true),
        )

        val restored = CrashReportSerializer.fromJson(CrashReportSerializer.toJson(original))

        assertEquals(original, restored)
    }

    @Test
    fun survivesTrickyStringContent() {
        // Stack traces and messages carry quotes, backslashes, and newlines — the
        // codec must escape and restore them exactly.
        val original = testReport(throwableMessage = "path C:\\tmp \"quoted\"\nline2\ttab")
        val restored = CrashReportSerializer.fromJson(CrashReportSerializer.toJson(original))
        assertEquals(original.throwableMessage, restored?.throwableMessage)
    }

    @Test
    fun handlesNullMessage() {
        val original = testReport(throwableMessage = null)
        val restored = CrashReportSerializer.fromJson(CrashReportSerializer.toJson(original))
        assertNull(restored?.throwableMessage)
        assertEquals(original, restored)
    }

    @Test
    fun returnsNullForCorruptInput() {
        // A half-written file (process killed mid-write) must not throw.
        assertNull(CrashReportSerializer.fromJson("{ this is not json"))
        assertNull(CrashReportSerializer.fromJson(""))
    }
}
