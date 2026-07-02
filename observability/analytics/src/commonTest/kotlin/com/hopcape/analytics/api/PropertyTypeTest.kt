package com.hopcape.analytics.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PropertyTypeTest {

    @Test
    fun matches_acceptsCorrectTypes() {
        assertTrue(PropertyType.STRING.matches("hi"))
        assertTrue(PropertyType.INT.matches(1))
        assertTrue(PropertyType.LONG.matches(1L))
        assertTrue(PropertyType.DOUBLE.matches(1.0))
        assertTrue(PropertyType.BOOLEAN.matches(true))
    }

    @Test
    fun matches_rejectsWrongTypes() {
        assertFalse(PropertyType.STRING.matches(1))
        assertFalse(PropertyType.INT.matches("1"))
        assertFalse(PropertyType.BOOLEAN.matches(0))
    }

    @Test
    fun number_acceptsAnyNumericType_butNotStrings() {
        assertTrue(PropertyType.NUMBER.matches(1))
        assertTrue(PropertyType.NUMBER.matches(1L))
        assertTrue(PropertyType.NUMBER.matches(1.5))
        assertFalse(PropertyType.NUMBER.matches("1"))
    }

    @Test
    fun any_acceptsEverything() {
        assertTrue(PropertyType.ANY.matches("s"))
        assertTrue(PropertyType.ANY.matches(1))
        assertTrue(PropertyType.ANY.matches(true))
    }
}
