package com.hopcape.odo.infrastructure.firebase.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirebaseEventSanitizerTest {

    // ── Event names ─────────────────────────────────────────────

    @Test
    fun validEventName_isKeptAsIs() {
        val sanitized = FirebaseEventSanitizer().sanitizeEvent("bill_scanned", emptyMap())
        assertEquals("bill_scanned", sanitized?.name)
    }

    @Test
    fun eventName_tooLong_isDropped() {
        val diagnostics = mutableListOf<String>()
        val name = "a".repeat(41)
        val sanitized = FirebaseEventSanitizer(onDiagnostic = { diagnostics += it }).sanitizeEvent(name, emptyMap())
        assertNull(sanitized)
        assertTrue(diagnostics.single().contains("invalid event name"))
    }

    @Test
    fun eventName_startingWithDigit_isDropped() {
        assertNull(FirebaseEventSanitizer().sanitizeEvent("1bill", emptyMap()))
    }

    @Test
    fun eventName_withInvalidCharacters_isDropped() {
        assertNull(FirebaseEventSanitizer().sanitizeEvent("bill-scanned", emptyMap()))
    }

    @Test
    fun eventName_withReservedPrefix_isDropped() {
        assertNull(FirebaseEventSanitizer().sanitizeEvent("firebase_campaign", emptyMap()))
        assertNull(FirebaseEventSanitizer().sanitizeEvent("google_ad_click", emptyMap()))
        assertNull(FirebaseEventSanitizer().sanitizeEvent("ga_session", emptyMap()))
    }

    // ── Param names ─────────────────────────────────────────────

    @Test
    fun invalidParamName_isDropped_eventStillSent() {
        val diagnostics = mutableListOf<String>()
        val sanitized = FirebaseEventSanitizer(onDiagnostic = { diagnostics += it })
            .sanitizeEvent("bill_scanned", mapOf("bad-name" to "x", "odometer" to 45210))

        assertEquals("bill_scanned", sanitized?.name)
        assertEquals(mapOf("odometer" to 45210L), sanitized?.parameters)
        assertTrue(diagnostics.single().contains("bad-name"))
    }

    @Test
    fun paramCount_capped_at25_extrasDroppedAndReported() {
        val diagnostics = mutableListOf<String>()
        val properties = (1..30).associate { "p$it" to it }
        val sanitized = FirebaseEventSanitizer(onDiagnostic = { diagnostics += it })
            .sanitizeEvent("bulk_event", properties)

        assertEquals(25, sanitized?.parameters?.size)
        assertTrue(diagnostics.any { it.contains("exceeds 25 params") })
    }

    // ── Value coercion ──────────────────────────────────────────

    @Test
    fun stringValue_isTruncatedAt100Chars() {
        val long = "x".repeat(150)
        val sanitized = FirebaseEventSanitizer().sanitizeEvent("e", mapOf("note" to long))
        assertEquals(100, (sanitized?.parameters?.get("note") as String).length)
    }

    @Test
    fun intValue_isWidenedToLong() {
        val sanitized = FirebaseEventSanitizer().sanitizeEvent("e", mapOf("odometer" to 45210))
        assertEquals(45210L, sanitized?.parameters?.get("odometer"))
    }

    @Test
    fun floatValue_isWidenedToDouble() {
        val sanitized = FirebaseEventSanitizer().sanitizeEvent("e", mapOf("amount" to 199.5f))
        assertEquals(199.5, sanitized?.parameters?.get("amount"))
    }

    @Test
    fun booleanValue_isCoercedToString() {
        val sanitized = FirebaseEventSanitizer().sanitizeEvent("e", mapOf("coldStart" to true))
        assertEquals("true", sanitized?.parameters?.get("coldStart"))
    }

    @Test
    fun nullValue_isDroppedAndReported() {
        val diagnostics = mutableListOf<String>()
        val sanitized = FirebaseEventSanitizer(onDiagnostic = { diagnostics += it })
            .sanitizeEvent("e", mapOf("workshop" to null, "odometer" to 1))

        assertEquals(mapOf("odometer" to 1L), sanitized?.parameters)
        assertTrue(diagnostics.single().contains("workshop"))
    }

    @Test
    fun unknownType_fallsBackToToString() {
        val sanitized = FirebaseEventSanitizer().sanitizeEvent("e", mapOf("id" to 42.toBigString()))
        assertEquals("BigString(42)", sanitized?.parameters?.get("id"))
    }

    private class BigString(val value: Int) {
        override fun toString() = "BigString($value)"
    }

    private fun Int.toBigString() = BigString(this)

    // ── User properties ─────────────────────────────────────────

    @Test
    fun userPropertyName_tooLong_isDroppedAndReported() {
        val diagnostics = mutableListOf<String>()
        val name = "a".repeat(25)
        val result = FirebaseEventSanitizer(onDiagnostic = { diagnostics += it }).sanitizeUserPropertyName(name)

        assertNull(result)
        assertTrue(diagnostics.single().contains(name))
    }

    @Test
    fun userPropertyValue_isTruncatedAt36Chars() {
        val long = "x".repeat(50)
        assertEquals(36, FirebaseEventSanitizer().sanitizeUserPropertyValue(long).length)
    }
}
