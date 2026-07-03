package com.hopcape.odo.core.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkshopNameTest {

    @Test
    fun validName_isTrimmed() {
        assertEquals("Sharma Motors", WorkshopName.of("  Sharma Motors  ").getOrNull()?.value)
    }

    @Test
    fun nullOrBlank_mapsToNullNotError() {
        val fromNull = WorkshopName.of(null)
        assertTrue(fromNull.isRight())
        assertNull(fromNull.getOrNull())

        val fromBlank = WorkshopName.of("   ")
        assertTrue(fromBlank.isRight())
        assertNull(fromBlank.getOrNull())
    }

    @Test
    fun atMaxLength_isAccepted() {
        val name = "a".repeat(WorkshopName.MAX_LENGTH)
        assertEquals(name, WorkshopName.of(name).getOrNull()?.value)
    }

    @Test
    fun tooLong_isRejected() {
        val result = WorkshopName.of("a".repeat(WorkshopName.MAX_LENGTH + 1))
        val error = result.leftOrNull()
        assertIs<DomainError.WorkshopNameTooLong>(error)
        assertEquals(WorkshopName.MAX_LENGTH, error.max)
    }
}
