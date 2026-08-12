package com.hopcape.crashreporting.internal.destinations

import com.hopcape.crashreporting.api.CrashSink
import com.hopcape.crashreporting.internal.model.Breadcrumb
import com.hopcape.crashreporting.testReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SinkCrashDestinationTest {

    private data class RecordedCall(
        val throwableType: String,
        val throwableMessage: String?,
        val stackTrace: String,
        val isFatal: Boolean,
        val breadcrumbs: List<String>,
        val customKeys: Map<String, Any?>,
    )

    private class RecordingSink(override val name: String = "vendor") : CrashSink {
        var recorded: RecordedCall? = null
        val customKeys = mutableListOf<Pair<String, Any?>>()
        var userId: String? = null
            private set

        override fun record(
            throwableType: String,
            throwableMessage: String?,
            stackTrace: String,
            isFatal: Boolean,
            breadcrumbs: List<String>,
            customKeys: Map<String, Any?>,
        ) {
            recorded = RecordedCall(throwableType, throwableMessage, stackTrace, isFatal, breadcrumbs, customKeys)
        }

        override fun setCustomKey(key: String, value: Any?) {
            customKeys += key to value
        }

        override fun setUserId(userId: String?) {
            this.userId = userId
        }
    }

    @Test
    fun name_isDelegated() {
        assertEquals("vendor", SinkCrashDestination(RecordingSink()).name)
    }

    @Test
    fun record_forwardsResolvedFields_notTheInternalReport() {
        val sink = RecordingSink()
        val destination = SinkCrashDestination(sink)
        val report = testReport(
            throwableType = "IllegalStateException",
            throwableMessage = "boom",
            breadcrumbs = listOf(Breadcrumb(timestampMs = 1L, tag = "AUTH", message = "login started")),
            customKeys = mapOf("car_count" to 2),
        )

        destination.record(report)

        val (type, message, stackTrace, isFatal, breadcrumbs, customKeys) = sink.recorded!!
        assertEquals("IllegalStateException", type)
        assertEquals("boom", message)
        assertEquals(report.stackTrace, stackTrace)
        assertEquals(false, isFatal)
        assertEquals(listOf("AUTH: login started"), breadcrumbs)
        assertEquals(mapOf("car_count" to 2), customKeys)
    }

    @Test
    fun setCustomKey_andSetUserId_areForwarded() {
        val sink = RecordingSink()
        val destination = SinkCrashDestination(sink)

        destination.setCustomKey("plan", "pro")
        destination.setUserId("u-1")

        assertEquals("plan" to "pro", sink.customKeys.single())
        assertEquals("u-1", sink.userId)
    }

    @Test
    fun aThrowingSink_propagates_soTheCallerMustWrapItInSafeCrashDestination() {
        val destination = SinkCrashDestination(object : CrashSink {
            override val name = "boom"
            override fun record(
                throwableType: String,
                throwableMessage: String?,
                stackTrace: String,
                isFatal: Boolean,
                breadcrumbs: List<String>,
                customKeys: Map<String, Any?>,
            ): Unit = throw IllegalStateException("boom")

            override fun setCustomKey(key: String, value: Any?) = throw IllegalStateException("boom")
            override fun setUserId(userId: String?) = throw IllegalStateException("boom")
        })

        assertFailsWith<IllegalStateException> { destination.record(testReport()) }
    }
}
