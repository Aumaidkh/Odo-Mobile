package com.hopcape.odo.feature.support.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much of an address a screen may show.
 *
 * Enough for the owner to recognise which of their addresses it is, and not enough for a
 * screenshot of the screen — or a navigation key written into saved state — to hand it to
 * anybody.
 */
class MaskEmailTest {

    @Test
    fun `the first letter and the whole domain survive`() {
        assertEquals("r•••@gmail.com", maskEmail("rakesh@gmail.com"))
    }

    @Test
    fun `a one letter local part is still masked`() {
        assertEquals("a•••@x.co", maskEmail("a@x.co"))
    }

    /** The only way this happens is data arriving in a shape nobody expected. */
    @Test
    fun `something that is not an address is masked whole`() {
        assertEquals("•••", maskEmail("rakesh"))
        assertEquals("•••", maskEmail("@gmail.com"))
        assertEquals("•••", maskEmail("rakesh@"))
        assertEquals("•••", maskEmail(""))
    }

    @Test
    fun `the local part never survives past its first letter`() {
        assertTrue("akesh" !in maskEmail("rakesh@gmail.com"))
    }
}
