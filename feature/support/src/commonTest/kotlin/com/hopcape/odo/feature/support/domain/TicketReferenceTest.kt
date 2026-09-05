package com.hopcape.odo.feature.support.domain

import com.hopcape.odo.core.domain.support.SupportTicketId
import com.hopcape.odo.core.domain.support.TicketReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The code an owner reads down a phone line.
 *
 * Its shape is the whole contract: the panel computes the same string from the same row, so a
 * change here silently stops two people talking about the same ticket.
 */
class TicketReferenceTest {

    @Test
    fun `it is two blocks under the Odo prefix`() {
        val reference = TicketReference.of(SupportTicketId("8f4219c7-1234-4000-8000-000000000000"))

        assertEquals(3, reference.split('-').size)
        assertTrue(reference.startsWith("ODO-"))
        assertEquals(listOf(4, 4), reference.split('-').drop(1).map { it.length })
    }

    /** The same row must never produce two codes. */
    @Test
    fun `the same id always gives the same code`() {
        val id = SupportTicketId("8f4219c7-1234-4000-8000-000000000000")

        assertEquals(TicketReference.of(id), TicketReference.of(id))
    }

    @Test
    fun `different ids give different codes`() {
        val first = TicketReference.of(SupportTicketId("8f4219c7-0000-4000-8000-000000000000"))
        val second = TicketReference.of(SupportTicketId("1a2b3c4d-0000-4000-8000-000000000000"))

        assertTrue(first != second)
    }

    /**
     * Nothing that reads as something else down a phone line.
     *
     * Crockford's base 32 leaves out I, L, O and U for exactly this reason — the prefix is the
     * only place an O is allowed, because it is a word.
     */
    @Test
    fun `the blocks avoid the letters that are misheard`() {
        val blocks = TicketReference.of(SupportTicketId("iloveuiloveu-0000-0000")).split('-').drop(1)

        val used = blocks.joinToString("").toSet()
        assertTrue(used.none { it in setOf('I', 'L', 'O', 'U') }, "got $blocks")
    }

    /** A label that will not render is worse than a short one. */
    @Test
    fun `an id shorter than the blocks still gives a code`() {
        val reference = TicketReference.of(SupportTicketId("ab"))

        assertEquals(3, reference.split('-').size)
        assertEquals(listOf(4, 4), reference.split('-').drop(1).map { it.length })
    }
}
