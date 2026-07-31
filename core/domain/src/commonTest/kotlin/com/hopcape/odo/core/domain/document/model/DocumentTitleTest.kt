package com.hopcape.odo.core.domain.document.model

import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DocumentTitleTest {

    @Test
    fun nullInput_isAbsent() {
        assertNull(DocumentTitle.of(null).getOrNull())
    }

    @Test
    fun blankInput_isAbsent() {
        assertNull(DocumentTitle.of("   ").getOrNull())
    }

    @Test
    fun surroundingWhitespace_isTrimmed() {
        assertEquals("SafeDrive comprehensive", DocumentTitle.of("  SafeDrive comprehensive ").getOrNull()?.value)
    }

    @Test
    fun maxLength_isAccepted() {
        val atLimit = "x".repeat(DocumentTitle.MAX_LENGTH)
        assertEquals(atLimit, DocumentTitle.of(atLimit).getOrNull()?.value)
    }

    @Test
    fun overMaxLength_isRejected() {
        val error = DocumentTitle.of("x".repeat(DocumentTitle.MAX_LENGTH + 1)).leftOrNull()
        assertEquals(DocumentTitle.MAX_LENGTH, assertIs<DomainError.DocumentTitleTooLong>(error).max)
    }

    @Test
    fun lengthIsMeasuredAfterTrimming() {
        val padded = "  " + "x".repeat(DocumentTitle.MAX_LENGTH) + "  "
        assertEquals(DocumentTitle.MAX_LENGTH, DocumentTitle.of(padded).getOrNull()?.value?.length)
    }
}
