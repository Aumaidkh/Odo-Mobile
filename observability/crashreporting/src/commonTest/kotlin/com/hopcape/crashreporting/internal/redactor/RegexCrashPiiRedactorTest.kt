package com.hopcape.crashreporting.internal.redactor

import com.hopcape.crashreporting.testReport
import com.hopcape.crashreporting.internal.model.Breadcrumb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegexCrashPiiRedactorTest {

    private val redactor = RegexCrashPiiRedactor()

    @Test
    fun masksEmailInThrowableMessage() {
        val report = testReport(throwableMessage = "failed for user john.doe@example.com")
        val redacted = redactor.redact(report)
        assertFalse(redacted.throwableMessage!!.contains("john.doe@example.com"))
        assertTrue(redacted.throwableMessage.contains("***email_masked***"))
    }

    @Test
    fun masksPhoneInBreadcrumbs() {
        val report = testReport(
            breadcrumbs = listOf(Breadcrumb(1L, "OTP", "sent code to 9876543210")),
        )
        val redacted = redactor.redact(report)
        assertEquals("sent code to ***phone_masked***", redacted.breadcrumbs.single().message)
    }

    @Test
    fun masksStringCustomKeys_butLeavesNonStringsUntouched() {
        val report = testReport(
            customKeys = mapOf("contact" to "reach me@x.io", "attempts" to 3),
        )
        val redacted = redactor.redact(report)
        assertEquals("reach ***email_masked***", redacted.customKeys["contact"])
        assertEquals(3, redacted.customKeys["attempts"], "non-String values must pass through unchanged")
    }

    @Test
    fun leavesCleanReportUnchanged() {
        val report = testReport(throwableMessage = "nothing sensitive here")
        assertEquals("nothing sensitive here", redactor.redact(report).throwableMessage)
    }
}
