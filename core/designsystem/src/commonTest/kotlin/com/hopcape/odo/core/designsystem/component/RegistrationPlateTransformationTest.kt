package com.hopcape.odo.core.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistrationPlateTransformationTest {

    @Test
    fun `groups a standard plate by character class`() {
        assertEquals("MH 12 AB 1234", formatRegistrationNumber("MH12AB1234"))
    }

    @Test
    fun `groups a BH-series plate`() {
        assertEquals("22 BH 1234 AA", formatRegistrationNumber("22BH1234AA"))
    }

    @Test
    fun `normalizes case and separators before grouping`() {
        assertEquals("MH 12 AB 1234", formatRegistrationNumber(" mh-12 ab/1234 "))
    }

    @Test
    fun `groups partial input as it is typed`() {
        val typed = listOf("M", "MH", "MH1", "MH12", "MH12A", "MH12AB", "MH12AB1")
        val expected = listOf("M", "MH", "MH 1", "MH 12", "MH 12 A", "MH 12 AB", "MH 12 AB 1")
        assertEquals(expected, typed.map(::formatRegistrationNumber))
    }

    @Test
    fun `empty input formats to empty`() {
        assertEquals("", formatRegistrationNumber(""))
    }

    @Test
    fun `input is uppercased alphanumeric and capped`() {
        assertEquals("MH12AB1234", "mh 12-ab 1234".asRegistrationInput(maxLength = 11))
        assertEquals("MH12AB", "MH12AB1234".asRegistrationInput(maxLength = 6))
    }

    @Test
    fun `cursor maps across every offset of a full plate`() {
        val raw = "MH12AB1234"
        val mapping = RegistrationPlateTransformation.filter(AnnotatedString(raw)).offsetMapping
        // Each raw offset lands on a formatted offset that has the same number of
        // non-space characters before it, and round-trips back to itself.
        val formatted = formatRegistrationNumber(raw)
        for (offset in 0..raw.length) {
            val transformed = mapping.originalToTransformed(offset)
            assertEquals(offset, formatted.take(transformed).count { it != ' ' }, "at $offset")
            assertEquals(offset, mapping.transformedToOriginal(transformed), "round-trip at $offset")
        }
    }

    @Test
    fun `cursor maps back from every offset of the formatted plate`() {
        val raw = "MH12AB1234"
        val formatted = formatRegistrationNumber(raw) // "MH 12 AB 1234"
        val mapping = RegistrationPlateTransformation.filter(AnnotatedString(raw)).offsetMapping
        for (offset in 0..formatted.length) {
            assertEquals(
                formatted.take(offset).count { it != ' ' },
                mapping.transformedToOriginal(offset),
                "at $offset",
            )
        }
    }

    @Test
    fun `cursor mapping is safe at the ends of an empty field`() {
        val mapping = RegistrationPlateTransformation.filter(AnnotatedString("")).offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(0, mapping.transformedToOriginal(0))
    }
}
