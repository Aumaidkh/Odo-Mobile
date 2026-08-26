package com.hopcape.logging.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiagnosticReferenceTest {

    private val install = "9f8c2b1e-0d4a-4f77-9c31-2a5b6d7e8f90"

    @Test
    fun create_producesTheOdoDashedShape() {
        val reference = DiagnosticReference.create(install, atEpochMs = 1_754_663_600_000L)

        assertTrue(
            Regex("^ODO-[0-9A-Z]{4}-[0-9A-Z]{4}$").matches(reference),
            "unexpected shape: $reference",
        )
    }

    @Test
    fun create_neverUsesTheAmbiguousLetters() {
        // Crockford's alphabet leaves out I, L, O and U so a code read over the phone cannot
        // be heard as a different one. The literal "ODO" prefix is not part of that check.
        val codes = (0..500L).map { DiagnosticReference.create("install-$it", atEpochMs = it * 1_000_000L) }

        codes.forEach { code ->
            val blocks = code.removePrefix("ODO-")
            assertTrue(blocks.none { it in "ILOU" }, "ambiguous letter in $code")
        }
    }

    @Test
    fun create_keepsTheInstallBlockStableAcrossTime() {
        val morning = DiagnosticReference.create(install, atEpochMs = 1_754_663_600_000L)
        val evening = DiagnosticReference.create(install, atEpochMs = 1_754_700_000_000L)

        assertEquals(morning.installBlock(), evening.installBlock())
        assertNotEquals(morning.timeBlock(), evening.timeBlock())
    }

    @Test
    fun create_givesTwoInstallationsDifferentInstallBlocks() {
        val one = DiagnosticReference.create(install, atEpochMs = 1_754_663_600_000L)
        // Same first characters on purpose: the block is a hash, not a slice, so ids that
        // start alike must not collide.
        val two = DiagnosticReference.create("9f8c2b1e-0d4a-4f77-9c31-2a5b6d7e8f91", atEpochMs = 1_754_663_600_000L)

        assertNotEquals(one.installBlock(), two.installBlock())
    }

    @Test
    fun create_isTheSameCodeForTheSameSecond() {
        val first = DiagnosticReference.create(install, atEpochMs = 1_754_663_600_123L)
        val second = DiagnosticReference.create(install, atEpochMs = 1_754_663_600_987L)

        assertEquals(first, second)
    }

    private fun String.installBlock() = split("-")[1]
    private fun String.timeBlock() = split("-")[2]
}
