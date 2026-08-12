package com.hopcape.odo.core.data.health

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Orchestration only — error mapping, telemetry, and sync scheduling. The SQL behaviour
 * these used to exercise through a real database now lives in
 * [SqlDelightHealthScoreLocalDataSourceTest]; this suite drives [HealthScoreRepositoryImpl]
 * against a [FakeHealthScoreLocalDataSource] instead.
 */
class HealthScoreRepositoryImplTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

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

    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private class FakeHealthScoreLocalDataSource(
        private val insertThrows: Throwable? = null,
        private val latestResult: HealthSnapshot? = null,
        private val latestThrows: Throwable? = null,
        private val latestOnOrBeforeResult: HealthSnapshot? = null,
        private val latestOnOrBeforeThrows: Throwable? = null,
        private val history: Flow<List<HealthSnapshot>> = flowOf(emptyList()),
    ) : HealthScoreLocalDataSource {
        var inserted: HealthSnapshot? = null
            private set

        override suspend fun insert(snapshot: HealthSnapshot) {
            insertThrows?.let { throw it }
            inserted = snapshot
        }

        override suspend fun latest(carId: CarId): HealthSnapshot? {
            latestThrows?.let { throw it }
            return latestResult
        }

        override suspend fun latestOnOrBefore(carId: CarId, instant: Instant): HealthSnapshot? {
            latestOnOrBeforeThrows?.let { throw it }
            return latestOnOrBeforeResult
        }

        override fun observeHistory(carId: CarId): Flow<List<HealthSnapshot>> = history
    }

    /**
     * A fresh, unexercised sync stack — [HealthScoreRepositoryImpl] still takes a
     * [SyncRunner] to construct, but nothing in this suite calls `syncWith`, so a
     * throwaway in-memory DB is all it needs.
     */
    private fun repo(
        local: HealthScoreLocalDataSource,
        crash: CrashRecorder = RecordingCrash(),
        scheduler: SyncScheduler = RecordingScheduler(),
    ) = HealthScoreRepositoryImpl(
        local = local,
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = crash),
        scheduler = scheduler,
    )

    private fun score() = HealthScore(
        factors = listOf(
            HealthFactor.of(HealthFactorKind.MAINTENANCE, 28),
            HealthFactor.of(HealthFactorKind.DOCUMENTATION, 24),
            HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, 14),
            HealthFactor.of(HealthFactorKind.HISTORY, 8),
        ),
    )

    private fun snapshot(id: String = "snap-1") = HealthSnapshot(
        id = HealthSnapshotId(id),
        carId = carId,
        ownerId = ownerId,
        score = score(),
        computedAt = Instant.parse("2026-08-01T10:00:00Z"),
        algoVersion = HealthScoreCalculator.RULES_VERSION,
    )

    @Test
    fun record_success_writesThroughLocalAndAsksForASync() = runTest {
        val local = FakeHealthScoreLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler = scheduler).record(snapshot())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("snap-1", local.inserted?.id?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun record_localThrows_isPersistenceFailure() = runTest {
        val local = FakeHealthScoreLocalDataSource(insertThrows = RuntimeException("disk full"))
        val crash = RecordingCrash()

        val result = repo(local, crash = crash).record(snapshot())

        assertTrue(result.isLeft(), "expected Left but was $result")
        assertEquals(1, crash.nonFatals.size, "a swallowed exception must still reach the dashboard")
    }

    @Test
    fun latest_passesThroughTheLocalResult() = runTest {
        val expected = snapshot()
        val local = FakeHealthScoreLocalDataSource(latestResult = expected)

        assertEquals(expected, repo(local).latest(carId))
    }

    @Test
    fun latest_localThrows_isNullAndReported() = runTest {
        val local = FakeHealthScoreLocalDataSource(latestThrows = RuntimeException("disk error"))
        val crash = RecordingCrash()

        assertNull(repo(local, crash = crash).latest(carId))
        assertEquals(1, crash.nonFatals.size)
    }

    @Test
    fun latestOnOrBefore_passesThroughTheLocalResult() = runTest {
        val expected = snapshot()
        val local = FakeHealthScoreLocalDataSource(latestOnOrBeforeResult = expected)

        assertEquals(expected, repo(local).latestOnOrBefore(carId, Instant.parse("2026-07-01T10:00:00Z")))
    }

    @Test
    fun latestOnOrBefore_localThrows_isNullAndReported() = runTest {
        val local = FakeHealthScoreLocalDataSource(latestOnOrBeforeThrows = RuntimeException("disk error"))
        val crash = RecordingCrash()

        assertNull(repo(local, crash = crash).latestOnOrBefore(carId, Instant.parse("2026-07-01T10:00:00Z")))
        assertEquals(1, crash.nonFatals.size)
    }

    @Test
    fun observeHistory_passesThroughTheLocalStream() = runTest {
        val expected = listOf(snapshot())
        val local = FakeHealthScoreLocalDataSource(history = flowOf(expected))

        assertEquals(expected, repo(local).observeHistory(carId).first())
    }

    @Test
    fun observeHistory_localThrows_emitsEmptyListInsteadAndReports() = runTest {
        val local = FakeHealthScoreLocalDataSource(history = flow { throw RuntimeException("read failed") })
        val crash = RecordingCrash()

        // The timeline drops its score events and still renders everything else.
        assertEquals(emptyList(), repo(local, crash = crash).observeHistory(carId).first())
        assertEquals(1, crash.nonFatals.size)
    }
}
