package com.hopcape.odo.infrastructure.firebase.crashlytics

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the fail-safe wrapping without a real Firebase project — [provider] lets a
 * test inject a throwing lookup, standing in for `Firebase.crashlytics` throwing because
 * no `FirebaseApp` has been configured (a missing google-services.json/plist).
 */
class RealFirebaseCrashlyticsGatewayTest {

    private fun gateway(diagnostics: MutableList<String>) = RealFirebaseCrashlyticsGateway(
        onDiagnostic = { diagnostics += it },
        provider = { throw IllegalStateException("no FirebaseApp") },
    )

    @Test
    fun recordException_withUnconfiguredFirebase_doesNotThrow() {
        gateway(mutableListOf()).recordException(RuntimeException("boom"))
    }

    @Test
    fun log_withUnconfiguredFirebase_doesNotThrow() {
        gateway(mutableListOf()).log("AUTH: token refreshed")
    }

    @Test
    fun setCustomKey_withUnconfiguredFirebase_doesNotThrow() {
        gateway(mutableListOf()).setCustomKey("car_count", "2")
    }

    @Test
    fun setUserId_withUnconfiguredFirebase_doesNotThrow() {
        gateway(mutableListOf()).setUserId("u-1")
    }

    @Test
    fun unconfiguredFirebase_reportsOnce_acrossMultipleCalls() {
        val diagnostics = mutableListOf<String>()
        val sut = gateway(diagnostics)

        sut.recordException(RuntimeException("boom"))
        sut.log("AUTH: token refreshed")
        sut.setCustomKey("car_count", "2")
        sut.setUserId("u-1")

        assertEquals(1, diagnostics.size, "the failed lookup is cached, not retried per call")
        assertTrue(diagnostics.single().contains("crashlytics: unavailable"))
    }

    @Test
    fun cancellationException_isNotSwallowed_itPropagates() {
        val sut = RealFirebaseCrashlyticsGateway(
            onDiagnostic = {},
            provider = { throw CancellationException("coroutine cancelled") },
        )

        assertFailsWith<CancellationException> { sut.recordException(RuntimeException("boom")) }
    }
}
