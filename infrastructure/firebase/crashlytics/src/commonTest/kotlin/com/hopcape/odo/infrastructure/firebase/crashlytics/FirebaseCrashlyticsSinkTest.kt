package com.hopcape.odo.infrastructure.firebase.crashlytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirebaseCrashlyticsSinkTest {

    private class FakeFirebaseCrashlyticsGateway : FirebaseCrashlyticsGateway {
        val loggedLines = mutableListOf<String>()
        val customKeys = mutableMapOf<String, String>()
        var recordedException: Throwable? = null
        var capturedUserId: String? = "unset"

        override fun recordException(throwable: Throwable) {
            recordedException = throwable
        }

        override fun log(message: String) {
            loggedLines += message
        }

        override fun setCustomKey(key: String, value: String) {
            customKeys[key] = value
        }

        override fun setUserId(userId: String?) {
            capturedUserId = userId
        }
    }

    @Test
    fun record_logsBreadcrumbs_setsCustomKeys_andRecordsTheException() {
        val gateway = FakeFirebaseCrashlyticsGateway()
        val sink = FirebaseCrashlyticsSink(gateway = gateway)

        sink.record(
            throwableType = "IllegalStateException",
            throwableMessage = "boom",
            stackTrace = "IllegalStateException: boom\n\tat Foo.bar(Foo.kt:1)",
            isFatal = false,
            breadcrumbs = listOf("AUTH: login started", "AUTH: token refreshed"),
            customKeys = mapOf("car_count" to 2, "plan" to null),
        )

        assertEquals(listOf("AUTH: login started", "AUTH: token refreshed"), gateway.loggedLines)
        assertEquals("2", gateway.customKeys["car_count"])
        assertEquals("null", gateway.customKeys["plan"])
        assertEquals("IllegalStateException: boom", gateway.recordedException?.message)
    }

    @Test
    fun setCustomKey_stringifiesTheValue() {
        val gateway = FakeFirebaseCrashlyticsGateway()
        val sink = FirebaseCrashlyticsSink(gateway = gateway)

        sink.setCustomKey("odometer", 45210L)

        assertEquals("45210", gateway.customKeys["odometer"])
    }

    @Test
    fun setUserId_isForwarded() {
        val gateway = FakeFirebaseCrashlyticsGateway()
        val sink = FirebaseCrashlyticsSink(gateway = gateway)

        sink.setUserId("u-1")
        assertEquals("u-1", gateway.capturedUserId)

        sink.setUserId(null)
        assertNull(gateway.capturedUserId)
    }

    @Test
    fun name_isCrashlytics() {
        assertEquals("crashlytics", FirebaseCrashlyticsSink(gateway = FakeFirebaseCrashlyticsGateway()).name)
    }
}
