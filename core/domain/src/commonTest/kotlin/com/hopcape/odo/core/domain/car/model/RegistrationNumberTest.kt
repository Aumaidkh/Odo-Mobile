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
    fun nullInput_yieldsNull() {
        assertNull(RegistrationNumber.of(null))
    }

    @Test
    fun blankInput_yieldsNull() {
        assertNull(RegistrationNumber.of("   "))
    }
}
