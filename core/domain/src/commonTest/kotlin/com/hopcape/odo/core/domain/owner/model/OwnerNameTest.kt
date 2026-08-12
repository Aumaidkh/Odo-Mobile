package com.hopcape.odo.core.domain.owner.model

import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OwnerNameTest {

    @Test
    fun validName_isTrimmed() {
        assertEquals("Rahul", OwnerName.of("  Rahul  ").getOrNull()?.value)
    }

    @Test
    fun singleWordName_isAccepted() {
        // Common in India — a name is not required to have a surname.
        assertEquals("Ravi", OwnerName.of("Ravi").getOrNull()?.value)
    }

    @Test
    fun nullOrBlank_isRejectedAsBlankNotTooShort() {
        // A name is mandatory here, unlike WorkshopName where absence maps to null.
        assertIs<DomainError.BlankOwnerName>(OwnerName.of(null).leftOrNull())
        assertIs<DomainError.BlankOwnerName>(OwnerName.of("").leftOrNull())
        // Whitespace only: blank after trimming, so it must not read as "too short".
        assertIs<DomainError.BlankOwnerName>(OwnerName.of("   ").leftOrNull())
    }

    @Test
    fun tooShort_isRejected() {
        val error = OwnerName.of("R").leftOrNull()
        assertIs<DomainError.OwnerNameTooShort>(error)
        assertEquals(OwnerName.MIN_LENGTH, error.min)
    }

    @Test
    fun atMinLength_isAccepted() {
        assertEquals("Jo", OwnerName.of("Jo").getOrNull()?.value)
    }

    @Test
    fun atMaxLength_isAccepted() {
        val name = "a".repeat(OwnerName.MAX_LENGTH)
        assertEquals(name, OwnerName.of(name).getOrNull()?.value)
    }

    @Test
    fun tooLong_isRejected() {
        val error = OwnerName.of("a".repeat(OwnerName.MAX_LENGTH + 1)).leftOrNull()
        assertIs<DomainError.OwnerNameTooLong>(error)
        assertEquals(OwnerName.MAX_LENGTH, error.max)
    }

    @Test
    fun lengthIsMeasuredAfterTrimming() {
        // Padding must not push a valid name over the limit.
        val padded = "  " + "a".repeat(OwnerName.MAX_LENGTH) + "  "
        assertEquals(OwnerName.MAX_LENGTH, OwnerName.of(padded).getOrNull()?.value?.length)
    }
}
