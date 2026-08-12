package com.hopcape.odo.core.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesTest {

    @Test
    fun validNotes_isTrimmed() {
        assertEquals("Oil change", Notes.of("  Oil change  ").getOrNull()?.value)
    }

    @Test
    fun nullOrBlank_mapsToNullNotError() {
        val fromNull = Notes.of(null)
        assertTrue(fromNull.isRight())
        assertNull(fromNull.getOrNull())

        val fromBlank = Notes.of("   ")
        assertTrue(fromBlank.isRight())
        assertNull(fromBlank.getOrNull())
    }

    @Test
    fun atMaxLength_isAccepted() {
        val text = "a".repeat(Notes.MAX_LENGTH)
        assertEquals(text, Notes.of(text).getOrNull()?.value)
    }

    @Test
    fun tooLong_isRejected() {
        val result = Notes.of("a".repeat(Notes.MAX_LENGTH + 1))
        val error = result.leftOrNull()
        assertIs<DomainError.NotesTooLong>(error)
        assertEquals(Notes.MAX_LENGTH, error.max)
    }
}
