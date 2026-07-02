package com.hopcape.logging.internal.redactor

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.internal.model.LogEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegexPiiRedactorTest {

    private fun eventWithFields(fields: Map<String, Any?>): LogEvent =
        LogEvent.Builder("Auth", "login")
            .level(LogLevel.INFO)
            .fields(fields)
            .build()

    @Test
    fun masksEmailInFieldValues() {
        val out = RegexPiiRedactor().redact(eventWithFields(mapOf("who" to "john.doe@example.com")))
        assertEquals("***email_masked***", out.fields["who"])
    }

    @Test
    fun masksTenDigitPhoneInFieldValues() {
        val out = RegexPiiRedactor().redact(eventWithFields(mapOf("mobile" to "9876543210")))
        assertEquals("***phone_masked***", out.fields["mobile"])
    }

    @Test
    fun leavesNonStringValuesUntouched() {
        val out = RegexPiiRedactor().redact(
            eventWithFields(mapOf("count" to 42, "ok" to true, "nothing" to null)),
        )
        assertEquals(42, out.fields["count"])
        assertEquals(true, out.fields["ok"])
        assertNull(out.fields["nothing"])
    }

    @Test
    fun leavesCleanStringsUntouched() {
        val out = RegexPiiRedactor().redact(eventWithFields(mapOf("city" to "Mumbai")))
        assertEquals("Mumbai", out.fields["city"])
    }

    @Test
    fun doesNotTouchTagOrEventMessage() {
        // PII in the message/tag is intentionally NOT redacted (only field values).
        val out = RegexPiiRedactor().redact(eventWithFields(mapOf("k" to "v")))
        assertEquals("Auth", out.tag)
        assertEquals("login", out.event)
    }

    @Test
    fun doesNotMutateTheOriginalEvent() {
        val original = eventWithFields(mapOf("who" to "a@b.com"))
        RegexPiiRedactor().redact(original)
        assertEquals("a@b.com", original.fields["who"], "redact must return a copy")
    }

    @Test
    fun honoursCustomPatterns() {
        val redactor = RegexPiiRedactor(patterns = mapOf("secret" to Regex("hunter2")))
        val out = redactor.redact(eventWithFields(mapOf("pw" to "my hunter2 pass")))
        assertTrue((out.fields["pw"] as String).contains("***secret_masked***"))
    }
}
