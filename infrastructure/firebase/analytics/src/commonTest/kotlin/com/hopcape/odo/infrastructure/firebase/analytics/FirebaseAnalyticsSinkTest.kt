package com.hopcape.odo.infrastructure.firebase.analytics

import com.hopcape.analytics.api.UserTraits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirebaseAnalyticsSinkTest {

    private class FakeFirebaseAnalyticsGateway : FirebaseAnalyticsGateway {
        val loggedEvents = mutableListOf<Pair<String, Map<String, Any>>>()
        var capturedUserId: String? = "unset"
        val userProperties = mutableMapOf<String, String>()

        override fun logEvent(name: String, parameters: Map<String, Any>) {
            loggedEvents += name to parameters
        }

        override fun setUserId(id: String?) {
            capturedUserId = id
        }

        override fun setUserProperty(name: String, value: String) {
            userProperties[name] = value
        }
    }

    @Test
    fun track_sanitizesThenForwardsToTheGateway() {
        val gateway = FakeFirebaseAnalyticsGateway()
        val sink = FirebaseAnalyticsSink(gateway = gateway)

        sink.track("bill_scanned", mapOf("odometer" to 45210, "workshop" to "auto-care"), timestampMs = 123L)

        val (name, params) = gateway.loggedEvents.single()
        assertEquals("bill_scanned", name)
        assertEquals(45210L, params["odometer"])
        assertEquals("auto-care", params["workshop"])
    }

    @Test
    fun track_withInvalidEventName_isDropped_gatewayNeverCalled() {
        val gateway = FakeFirebaseAnalyticsGateway()
        val sink = FirebaseAnalyticsSink(gateway = gateway)

        sink.track("firebase_reserved", emptyMap(), timestampMs = 0L)

        assertTrue(gateway.loggedEvents.isEmpty())
    }

    @Test
    fun identify_forwardsUserIdAndSanitizedTraits() {
        val gateway = FakeFirebaseAnalyticsGateway()
        val sink = FirebaseAnalyticsSink(gateway = gateway)

        sink.identify(UserTraits("u-1", mapOf("city" to "Mumbai", "plan" to "pro")))

        assertEquals("u-1", gateway.capturedUserId)
        assertEquals("Mumbai", gateway.userProperties["city"])
        assertEquals("pro", gateway.userProperties["plan"])
    }

    @Test
    fun identify_dropsTraitWithInvalidName_keepsTheRest() {
        val gateway = FakeFirebaseAnalyticsGateway()
        val sink = FirebaseAnalyticsSink(gateway = gateway)

        sink.identify(UserTraits("u-1", mapOf("bad-name" to "x", "city" to "Mumbai")))

        assertNull(gateway.userProperties["bad-name"])
        assertEquals("Mumbai", gateway.userProperties["city"])
    }

    @Test
    fun identify_dropsNullTraitValue_reportsDiagnostic() {
        val gateway = FakeFirebaseAnalyticsGateway()
        val diagnostics = mutableListOf<String>()
        val sink = FirebaseAnalyticsSink(gateway = gateway, onDiagnostic = { diagnostics += it })

        sink.identify(UserTraits("u-1", mapOf("city" to null)))

        assertTrue(gateway.userProperties.isEmpty())
        assertTrue(diagnostics.single().contains("city"))
    }

    @Test
    fun name_isFirebase() {
        assertEquals("firebase", FirebaseAnalyticsSink(gateway = FakeFirebaseAnalyticsGateway()).name)
    }

    @Test
    fun flush_doesNotThrow() {
        FirebaseAnalyticsSink(gateway = FakeFirebaseAnalyticsGateway()).flush()
    }
}
