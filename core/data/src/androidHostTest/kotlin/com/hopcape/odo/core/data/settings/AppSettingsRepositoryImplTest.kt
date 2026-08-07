package com.hopcape.odo.core.data.settings

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Orchestration only — the missing-or-unreadable-row -> [AppSettings.Default] policy, and
 * error mapping on save. The SQL behaviour these used to exercise through a real database
 * now lives in [SqlDelightAppSettingsLocalDataSourceTest]; this suite drives
 * [AppSettingsRepositoryImpl] against a [FakeAppSettingsLocalDataSource] instead.
 */
class AppSettingsRepositoryImplTest {

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

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    private class FakeAppSettingsLocalDataSource(
        private val saveThrows: Throwable? = null,
        private val observed: Flow<AppSettings?> = flowOf(null),
    ) : AppSettingsLocalDataSource {
        var saved: AppSettings? = null
            private set

        override suspend fun save(settings: AppSettings) {
            saveThrows?.let { throw it }
            saved = settings
        }

        override fun observe(): Flow<AppSettings?> = observed
    }

    private fun repo(local: AppSettingsLocalDataSource) = AppSettingsRepositoryImpl(
        local = local,
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
    )

    @Test
    fun observe_localAnswersNull_isDefault() = runTest {
        assertEquals(AppSettings.Default, repo(FakeAppSettingsLocalDataSource(observed = flowOf(null))).observe().first())
    }

    @Test
    fun observe_passesThroughANonNullValue() = runTest {
        val expected = AppSettings(theme = ThemePreference.DARK)
        val local = FakeAppSettingsLocalDataSource(observed = flowOf(expected))

        assertEquals(expected, repo(local).observe().first())
    }

    @Test
    fun observe_localThrows_isDefault() = runTest {
        val local = FakeAppSettingsLocalDataSource(observed = flow { throw RuntimeException("read failed") })

        assertEquals(AppSettings.Default, repo(local).observe().first())
    }

    @Test
    fun save_success_writesThroughLocal() = runTest {
        val local = FakeAppSettingsLocalDataSource()
        val settings = AppSettings(theme = ThemePreference.DARK)

        val result = repo(local).save(settings)

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals(settings, local.saved)
    }

    @Test
    fun save_localThrows_isPersistenceFailure() = runTest {
        val local = FakeAppSettingsLocalDataSource(saveThrows = RuntimeException("disk full"))

        val result = repo(local).save(AppSettings.Default)

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }
}
