package com.hopcape.performance.internal.redactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegexSpanPiiRedactorTest {

    private val redactor = RegexSpanPiiRedactor()

    @Test
    fun redact_masksEmail() {
        assertTrue(redactor.redact("contact user@example.com").contains("***email_masked***"))
    }

    @Test
    fun redact_masksTenDigitPhone() {
        assertTrue(redactor.redact("call 9876543210 now").contains("***phone_masked***"))
    }

    @Test
    fun redact_masksIndianRegistrationPlate() {
        assertTrue(redactor.redact("vehicle MH12AB1234 serviced").contains("***plate_masked***"))
    }

    @Test
    fun redact_leavesNonPiiTextUntouched() {
        assertEquals("engine_check_light", redactor.redact("engine_check_light"))
    }
}
