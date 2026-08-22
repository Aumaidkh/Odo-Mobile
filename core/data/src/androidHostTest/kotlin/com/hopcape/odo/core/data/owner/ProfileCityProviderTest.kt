package com.hopcape.odo.core.data.owner

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Orchestration only — [ProfileCityProvider] reads the same row
 * [SqlDelightProfileLocalDataSourceTest] already covers; this suite is just the
 * catch-and-report on top, driven against a fake port.
 */
class ProfileCityProviderTest {

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit
        override fun flush() = Unit
    }

    private class FakeSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            FakeSpan("span", traceId, parentSpanId, name)
        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private class RecordingCrash : CrashRecorder {
        val nonFatals = mutableListOf<Throwable>()
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
            nonFatals += throwable
        }
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    private class FakeProfileLocalDataSource(
        private val city: String? = null,
        private val currentCityThrows: Throwable? = null,
    ) : ProfileLocalDataSource {
        override suspend fun save(profile: OwnerProfile) = Unit
        override fun observe() = throw NotImplementedError("unused by ProfileCityProvider")
        override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber) =
            throw NotImplementedError("unused by ProfileCityProvider")
        override suspend fun softDeleteAll() = Unit
        override suspend fun currentCity(): String? {
            currentCityThrows?.let { throw it }
            return city
        }
    }

    private fun provider(local: ProfileLocalDataSource, crash: CrashRecorder = RecordingCrash()) = ProfileCityProvider(
        local = local,
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = crash),
    )

    @Test
    fun currentCity_passesThroughTheLocalValue() = runTest {
        assertEquals("Pune", provider(FakeProfileLocalDataSource(city = "Pune")).currentCity())
    }

    @Test
    fun currentCity_localAnswersNull_isNull() = runTest {
        assertNull(provider(FakeProfileLocalDataSource(city = null)).currentCity())
    }

    @Test
    fun currentCity_localThrows_isNullAndReported() = runTest {
        val crash = RecordingCrash()
        val local = FakeProfileLocalDataSource(currentCityThrows = RuntimeException("disk error"))

        assertNull(provider(local, crash).currentCity())

        assertEquals(1, crash.nonFatals.size, "an unreadable city must still reach the dashboard")
    }
}
