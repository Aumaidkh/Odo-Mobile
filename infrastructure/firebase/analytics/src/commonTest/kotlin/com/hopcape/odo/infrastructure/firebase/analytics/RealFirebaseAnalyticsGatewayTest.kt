package com.hopcape.odo.infrastructure.firebase.analytics

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the fail-safe wrapping without a real Firebase project — [provider] lets a
 * test inject a throwing lookup, standing in for `Firebase.analytics` throwing because no
 * `FirebaseApp` has been configured (a missing google-services.json/plist).
 */
class RealFirebaseAnalyticsGatewayTest {

    private fun gateway(diagnostics: MutableList<String>) = RealFirebaseAnalyticsGateway(
        onDiagnostic = { diagnostics += it },
        provider = { throw IllegalStateException("no FirebaseApp") },
    )

    @Test
    fun logEvent_withUnconfiguredFirebase_doesNotThrow_reportsUndelivered() {
        val delivered = gateway(mutableListOf()).logEvent("bill_scanned", mapOf("odometer" to 1L))
        assertEquals(false, delivered)
    }

    @Test
    fun setUserId_withUnconfiguredFirebase_doesNotThrow() {
        gateway(mutableListOf()).setUserId("u-1")
    }

    @Test
    fun setUserProperty_withUnconfiguredFirebase_doesNotThrow() {
        gateway(mutableListOf()).setUserProperty("city", "Mumbai")
    }

    @Test
    fun unconfiguredFirebase_reportsOnce_acrossMultipleCalls() {
        val diagnostics = mutableListOf<String>()
        val sut = gateway(diagnostics)

        sut.logEvent("bill_scanned", emptyMap())
        sut.setUserId("u-1")
        sut.setUserProperty("city", "Mumbai")

        assertEquals(1, diagnostics.size, "the failed lookup is cached, not retried per call")
        assertTrue(diagnostics.single().contains("analytics unavailable"))
    }

    @Test
    fun cancellationException_isNotSwallowed_itPropagates() {
        val sut = RealFirebaseAnalyticsGateway(
            onDiagnostic = {},
            provider = { throw CancellationException("coroutine cancelled") },
        )

        assertFailsWith<CancellationException> { sut.logEvent("bill_scanned", emptyMap()) }
    }
}
