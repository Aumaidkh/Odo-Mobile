package com.hopcape.odo.core.common.id

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UuidIdGeneratorTest {

    private val generator = UuidIdGenerator()

    @Test
    fun newId_isNotBlank() {
        assertTrue(generator.newId().isNotBlank())
    }

    @Test
    fun newId_isUnique_acrossCalls() {
        assertNotEquals(generator.newId(), generator.newId())
    }

    @Test
    fun newId_hasUuidShape() {
        // 8-4-4-4-12 hex groups, e.g. "f47ac10b-58cc-4372-a567-0e02b2c3d479".
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        val id = generator.newId()
        assertTrue(uuidRegex.matches(id), "expected UUID shape, got: $id")
    }

    @Test
    fun newId_has36Characters() {
        assertEquals(36, generator.newId().length)
    }
}
