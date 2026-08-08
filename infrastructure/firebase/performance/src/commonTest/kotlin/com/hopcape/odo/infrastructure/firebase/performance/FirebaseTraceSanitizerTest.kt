package com.hopcape.odo.infrastructure.firebase.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirebaseTraceSanitizerTest {

    @Test
    fun sanitizeName_acceptsAnOrdinaryName() {
        assertEquals("sync.run", FirebaseTraceSanitizer().sanitizeName("sync.run"))
    }

    @Test
    fun sanitizeName_rejectsLeadingUnderscore() {
        assertNull(FirebaseTraceSanitizer().sanitizeName("_internal"))
    }

    @Test
    fun sanitizeName_rejectsNameOverMaxLength() {
        assertNull(FirebaseTraceSanitizer().sanitizeName("x".repeat(101)))
    }

    @Test
    fun sanitizeName_rejectsBlankName() {
        assertNull(FirebaseTraceSanitizer().sanitizeName("   "))
    }

    @Test
    fun sanitizeName_reportsDiagnosticOnDrop() {
        val diagnostics = mutableListOf<String>()
        FirebaseTraceSanitizer(onDiagnostic = { diagnostics += it }).sanitizeName("_bad")

        assertTrue(diagnostics.single().contains("_bad"))
    }

    @Test
    fun sanitizeAttributes_dropsBeyondFiveAttributes() {
        val attributes = (1..8).associate { "attr$it" to "v$it" }

        val sanitized = FirebaseTraceSanitizer().sanitizeAttributes(attributes)

        assertEquals(5, sanitized.size)
    }

    @Test
    fun sanitizeAttributes_dropsReservedPrefix() {
        val sanitized = FirebaseTraceSanitizer().sanitizeAttributes(mapOf("firebase_internal" to "x"))

        assertTrue(sanitized.isEmpty())
    }

    @Test
    fun sanitizeAttributes_truncatesOverlongValue() {
        val sanitized = FirebaseTraceSanitizer().sanitizeAttributes(mapOf("key" to "v".repeat(150)))

        assertEquals(100, sanitized["key"]?.length)
    }

    @Test
    fun sanitizeAttributes_keepsWellFormedAttributes() {
        val sanitized = FirebaseTraceSanitizer().sanitizeAttributes(mapOf("entity" to "trip"))

        assertEquals("trip", sanitized["entity"])
    }
}
