package com.hopcape.odo.core.domain.car.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VinTest {

    private val valid = "MA3ERLF1S00123456"

    @Test
    fun seventeenValidCharacters_areAccepted() {
        assertEquals(valid, Vin.of(valid)?.value)
    }

    @Test
    fun lowercaseAndSpacing_areNormalized() {
        assertEquals(valid, Vin.of("ma3erlf1s 0012 3456")?.value)
    }

    @Test
    fun tooShortOrTooLong_isRejected() {
        assertNull(Vin.of(valid.dropLast(1)))
        assertNull(Vin.of(valid + "7"))
    }

    @Test
    fun excludedLetters_areRejected() {
        // I, O and Q are omitted by the standard so they can't be read as 1 and 0.
        assertNull(Vin.of("MA3ERLFIS00123456"))
        assertNull(Vin.of("MA3ERLFOS00123456"))
        assertNull(Vin.of("MA3ERLFQS00123456"))
    }

    @Test
    fun nullOrBlank_isAbsentRatherThanInvalid() {
        assertNull(Vin.of(null))
        assertNull(Vin.of("   "))
    }

    @Test
    fun formatted_splitsDescriptorFromSerial() {
        assertEquals("MA3ERLF1S 00123456", Vin.of(valid)?.formatted)
    }
}
