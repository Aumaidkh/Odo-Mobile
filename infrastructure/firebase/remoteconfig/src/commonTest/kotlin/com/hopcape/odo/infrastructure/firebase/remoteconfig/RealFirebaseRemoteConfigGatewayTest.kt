package com.hopcape.odo.infrastructure.firebase.remoteconfig

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the fail-safe wrapping without a real Firebase project — [provider] lets a
 * test inject a throwing lookup, standing in for `Firebase.remoteConfig` throwing because
 * no `FirebaseApp` has been configured (a missing google-services.json/plist). Mirrors
 * :infrastructure:firebase:analytics's RealFirebaseAnalyticsGatewayTest.
 */
class RealFirebaseRemoteConfigGatewayTest {

    private fun gateway(diagnostics: MutableList<String>) = RealFirebaseRemoteConfigGateway(
        minimumFetchIntervalSeconds = 3_600L,
        defaults = emptyMap(),
        onDiagnostic = { diagnostics += it },
        provider = { throw IllegalStateException("no FirebaseApp") },
    )

    @Test
    fun fetchAndActivate_withUnconfiguredFirebase_doesNotThrow_reportsNotActivated() = runTest {
        assertEquals(false, gateway(mutableListOf()).fetchAndActivate())
    }

    @Test
    fun long_withUnconfiguredFirebase_returnsNull() {
        assertNull(gateway(mutableListOf()).long("min_supported_version_code"))
    }

    @Test
    fun string_withUnconfiguredFirebase_returnsNull() {
        assertNull(gateway(mutableListOf()).string("maintenance_mode"))
    }

    @Test
    fun lastFetchAt_withUnconfiguredFirebase_isNull() {
        assertNull(gateway(mutableListOf()).lastFetchAt)
    }

    @Test
    fun unconfiguredFirebase_reportsOnce_acrossMultipleCalls() = runTest {
        val diagnostics = mutableListOf<String>()
        val sut = gateway(diagnostics)

        sut.fetchAndActivate()
        sut.long("min_supported_version_code")
        sut.string("maintenance_mode")

        assertEquals(1, diagnostics.size, "the failed lookup is cached, not retried per call")
        assertTrue(diagnostics.single().contains("unavailable"))
    }

    @Test
    fun cancellationException_isNotSwallowed_itPropagates() = runTest {
        val sut = RealFirebaseRemoteConfigGateway(
            minimumFetchIntervalSeconds = 3_600L,
            defaults = emptyMap(),
            onDiagnostic = {},
            provider = { throw CancellationException("coroutine cancelled") },
        )

        assertFailsWith<CancellationException> { sut.fetchAndActivate() }
    }
}
