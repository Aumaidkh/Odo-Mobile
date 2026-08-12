package com.hopcape.odo.core.data.owner

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.shared.DomainError
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Orchestration only — error mapping, telemetry, sync scheduling. The SQL behaviour these
 * used to exercise through a real database now lives in
 * [SqlDelightProfileLocalDataSourceTest]; this suite drives [OwnerProfileRepositoryImpl]
 * against a [FakeProfileLocalDataSource] instead.
 */
class OwnerProfileRepositoryImplTest {

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

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private class FakeProfileLocalDataSource(
        private val saveThrows: Throwable? = null,
        private val softDeleteAllThrows: Throwable? = null,
        private val observed: Flow<OwnerProfile?> = flowOf(null),
        private val currentCityResult: String? = null,
    ) : ProfileLocalDataSource {
        var saved: OwnerProfile? = null
            private set
        var softDeletedAll = false
            private set

        override suspend fun save(profile: OwnerProfile) {
            saveThrows?.let { throw it }
            saved = profile
        }

        override fun observe(): Flow<OwnerProfile?> = observed

        override suspend fun softDeleteAll() {
            softDeleteAllThrows?.let { throw it }
            softDeletedAll = true
        }

        override suspend fun currentCity(): String? = currentCityResult
    }

    private fun repo(local: ProfileLocalDataSource, scheduler: SyncScheduler = RecordingScheduler()) =
        OwnerProfileRepositoryImpl(
            local = local,
            telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
            scheduler = scheduler,
        )

    private fun profile(
        name: String = "Rahul",
        goal: OnboardingGoal = OnboardingGoal.TRACK_COSTS,
    ): OwnerProfile = OwnerProfile.new(
        id = ownerId,
        name = OwnerName.of(name).getOrNull()!!,
        goal = goal,
    )

    @Test
    fun save_success_writesThroughLocalAndAsksForASync() = runTest {
        val local = FakeProfileLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).save(profile())

        assertTrue(result.isRight(), "expected Right but was $result")
        assertEquals("Rahul", local.saved?.name?.value)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun save_localThrows_isPersistenceFailure() = runTest {
        val local = FakeProfileLocalDataSource(saveThrows = RuntimeException("disk full"))

        val result = repo(local).save(profile())

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun save_schedulingFailure_stillSucceeds() = runTest {
        val local = FakeProfileLocalDataSource()
        val exploding = object : SyncScheduler {
            override fun scheduleStartupSync() = Unit
            override fun requestSync(reason: SyncReason): Nothing = error("scheduler unavailable")
        }

        val result = repo(local, exploding).save(profile())

        assertTrue(result.isRight(), "a broken scheduler must not fail an already-committed write")
    }

    @Test
    fun delete_success_asksForASync() = runTest {
        val local = FakeProfileLocalDataSource()
        val scheduler = RecordingScheduler()

        val result = repo(local, scheduler).delete()

        assertTrue(result.isRight(), "expected Right but was $result")
        assertTrue(local.softDeletedAll)
        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun delete_localThrows_isPersistenceFailure() = runTest {
        val local = FakeProfileLocalDataSource(softDeleteAllThrows = RuntimeException("boom"))

        val result = repo(local).delete()

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun observe_passesThroughTheLocalStream() = runTest {
        val expected = profile()
        val local = FakeProfileLocalDataSource(observed = flowOf(expected))

        assertEquals(expected, repo(local).observe().first())
    }

    @Test
    fun observe_localThrows_emitsNullInstead() = runTest {
        val local = FakeProfileLocalDataSource(observed = flow { throw RuntimeException("read failed") })

        assertNull(repo(local).observe().first())
    }
}
