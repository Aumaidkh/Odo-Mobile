package com.hopcape.odo.core.domain.document.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocumentValidityTest {

    private val today = LocalDate(2026, 7, 28)

    @Test
    fun noExpiry_neverLapses() {
        val validity = DocumentValidity.of(expiresOn = null, today = today)
        assertEquals(DocumentValidity.NoExpiry, validity)
        assertTrue(validity.isInForce)
        assertFalse(validity.needsAttention)
    }

    @Test
    fun wellAhead_isValid() {
        val validity = DocumentValidity.of(LocalDate(2027, 7, 3), today)
        val valid = assertIs<DocumentValidity.Valid>(validity)
        assertEquals(340, valid.daysLeft)
        assertFalse(validity.needsAttention)
    }

    @Test
    fun insideRenewalWindow_isExpiringSoon() {
        val validity = DocumentValidity.of(LocalDate(2026, 8, 4), today)
        val soon = assertIs<DocumentValidity.ExpiringSoon>(validity)
        assertEquals(7, soon.daysLeft)
        assertTrue(validity.isInForce)
        assertTrue(validity.needsAttention)
    }

    @Test
    fun exactlyOnTheWindowEdge_isExpiringSoon() {
        val edge = LocalDate(2026, 8, 27) // today + 30
        assertIs<DocumentValidity.ExpiringSoon>(DocumentValidity.of(edge, today))
    }

    @Test
    fun oneDayPastTheWindow_isStillJustValid() {
        val outside = LocalDate(2026, 8, 28) // today + 31
        assertIs<DocumentValidity.Valid>(DocumentValidity.of(outside, today))
    }

    @Test
    fun expiringToday_isNotYetExpired() {
        val validity = DocumentValidity.of(today, today)
        val soon = assertIs<DocumentValidity.ExpiringSoon>(validity)
        assertEquals(0, soon.daysLeft)
        assertTrue(validity.isInForce)
    }

    @Test
    fun pastExpiry_isExpired() {
        val validity = DocumentValidity.of(LocalDate(2026, 6, 28), today)
        val expired = assertIs<DocumentValidity.Expired>(validity)
        assertEquals(30, expired.daysAgo)
        assertFalse(validity.isInForce)
        assertTrue(validity.needsAttention)
    }

    @Test
    fun renewalWindow_isConfigurable() {
        val inSixtyDays = LocalDate(2026, 9, 26)
        assertIs<DocumentValidity.Valid>(DocumentValidity.of(inSixtyDays, today))
        assertIs<DocumentValidity.ExpiringSoon>(
            DocumentValidity.of(inSixtyDays, today, renewalWindowDays = 90),
        )
    }
}
