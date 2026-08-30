package com.hopcape.odo.core.domain.car.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegistrationNumberTest {

    @Test
    fun normalizes_uppercaseAndStripsWhitespace() {
        assertEquals("MH12AB1234", RegistrationNumber.of("mh 12 ab 1234")?.value)
    }

    @Test
    fun normalizes_stripsPunctuation_soDifferentlyFormattedPlatesMatch() {
        assertEquals("MH12AB1234", RegistrationNumber.of("MH-12-AB-1234")?.value)
        assertEquals("MH12AB1234", RegistrationNumber.of("mh.12.ab.1234")?.value)
        assertEquals(
            RegistrationNumber.of("MH12AB1234"),
            RegistrationNumber.of("MH-12-AB-1234"),
            "the same plate, written differently, must be the same value",
        )
    }

    @Test
    fun nullInput_yieldsNull() {
        assertNull(RegistrationNumber.of(null))
    }

    @Test
    fun blankInput_yieldsNull() {
        assertNull(RegistrationNumber.of("   "))
    }
}
