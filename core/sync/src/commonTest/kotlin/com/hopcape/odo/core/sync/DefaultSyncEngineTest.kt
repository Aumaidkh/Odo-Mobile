package com.hopcape.odo.core.sync

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.sync.observability.SyncTelemetry
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The engine's two jobs: run the entities in dependency order, and stop the run the moment
 * one refuses. Both are correctness, not tidiness — out-of-order pushes and
 * carry-on-after-failure both end as foreign-key errors on the server.
 */
class DefaultSyncEngineTest {

    @Test
    fun entitiesRunInDeclarationOrder_whateverOrderTheyWereRegisteredIn() = runTest {
        val ran = mutableListOf<SyncEntity>()
        // Deliberately shuffled: Koin hands them over in module-registration order, which is
        // not something the engine's correctness may depend on.
        val engine = engine(
            FakeSyncable(SyncEntity.DOCUMENTS, ran),
            FakeSyncable(SyncEntity.PROFILES, ran),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
            FakeSyncable(SyncEntity.CARS, ran),
        )

        assertEquals(SyncResult.Success, engine.sync())
        assertEquals(
            listOf(SyncEntity.PROFILES, SyncEntity.CARS, SyncEntity.SERVICE_LOGS, SyncEntity.DOCUMENTS),
            ran,
        )
    }

    @Test
    fun aRefusalStopsTheRun_soChildrenOfAFailedParentAreNotAttempted() = runTest {
        val ran = mutableListOf<SyncEntity>()
        val engine = engine(
            FakeSyncable(SyncEntity.PROFILES, ran),
            FakeSyncable(SyncEntity.CARS, ran, accepts = false),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
        )

        val result = engine.sync()

        assertIs<SyncResult.Partial>(result)
        assertEquals(SyncEntity.CARS, result.failedAt)
        // service_logs never ran. Every log referencing a car the server has not seen would
        // have failed anyway, turning one failure into a log full of consequences.
        assertEquals(listOf(SyncEntity.PROFILES, SyncEntity.CARS), ran)
    }

    @Test
    fun aSyncableThatThrowsIsTreatedAsARefusalAndRecorded() = runTest {
        val ran = mutableListOf<SyncEntity>()
        val synchronizer = RecordingSynchronizer()
        val boom = IllegalStateException("connection reset")
        val engine = engine(
            FakeSyncable(SyncEntity.PROFILES, ran),
            FakeSyncable(SyncEntity.CARS, ran, throws = boom),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
            synchronizer = synchronizer,
        )

        val result = engine.sync()

        assertIs<SyncResult.Partial>(result)
        assertEquals(SyncEntity.CARS, result.failedAt)
        assertEquals(boom, result.cause)
        assertEquals(listOf(SyncEntity.PROFILES, SyncEntity.CARS), ran)
        val expected: List<Pair<SyncEntity, String?>> = listOf(SyncEntity.CARS to "IllegalStateException")
        assertEquals(expected, synchronizer.failures)
    }

    @Test
    fun aClosedGateSkipsTheRunEntirely() = runTest {
        val ran = mutableListOf<SyncEntity>()
        val engine = engine(FakeSyncable(SyncEntity.CARS, ran), gate = { false })

        val result = engine.sync()

        assertIs<SyncResult.Skipped>(result)
        // Not a single push attempted. An anonymous run can only collect 401s.
        assertTrue(ran.isEmpty())
    }

    @Test
    fun noRegisteredSyncablesIsSkipped_notSuccess() = runTest {
        // Reporting success for a run that did nothing would make an unwired graph look
        // healthy — the exact failure the debug row exists to catch.
        assertIs<SyncResult.Skipped>(engine().sync())
    }

    @Test
    fun everyEntityRunsWhenAllAccept() = runTest {
        val ran = mutableListOf<SyncEntity>()
        val engine = engine(
            FakeSyncable(SyncEntity.PROFILES, ran),
            FakeSyncable(SyncEntity.CARS, ran),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
            FakeSyncable(SyncEntity.OVERCHARGE_REPORTS, ran),
            FakeSyncable(SyncEntity.DOCUMENTS, ran),
            FakeSyncable(SyncEntity.HEALTH_SCORES, ran),
        )

        assertEquals(SyncResult.Success, engine.sync())
        assertEquals(6, ran.size)
    }

    @Test
    fun theRunningFlagIsClearedHoweverTheRunEnds() = runTest {
        val states = mutableListOf<Boolean>()
        val observer = SyncRunObserver { states += it }
        val ran = mutableListOf<SyncEntity>()

        // A refusal returns early, and a spinner left running is a bug report.
        engine(
            FakeSyncable(SyncEntity.CARS, ran, accepts = false),
            observer = observer,
        ).sync()

        assertEquals(listOf(true, false), states)
    }

    @Test
    fun aSkippedRunNeverClaimsToBeRunning() = runTest {
        val states = mutableListOf<Boolean>()
        engine(
            FakeSyncable(SyncEntity.CARS, mutableListOf()),
            gate = SyncGate { false },
            observer = SyncRunObserver { states += it },
        ).sync()

        assertTrue(states.isEmpty())
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun engine(
        vararg syncables: Syncable,
        synchronizer: Synchronizer = RecordingSynchronizer(),
        gate: SyncGate = SyncGate { true },
        observer: SyncRunObserver = SyncRunObserver { },
    ): SyncEngine = DefaultSyncEngine(
        syncables = syncables.toList(),
        synchronizer = synchronizer,
        telemetry = SyncTelemetry(NoopLogger, NoopAnalytics, NoopTracer, NoopCrash),
        gate = gate,
        observer = observer,
    )

    private class FakeSyncable(
        override val entity: SyncEntity,
        private val ran: MutableList<SyncEntity>,
        private val accepts: Boolean = true,
        private val throws: Throwable? = null,
    ) : Syncable {
        override suspend fun syncWith(synchronizer: Synchronizer): Boolean {
            ran += entity
            throws?.let { throw it }
            return accepts
        }
    }

    private class RecordingSynchronizer : Synchronizer {
        val failures = mutableListOf<Pair<SyncEntity, String?>>()
        private val cursors = mutableMapOf<SyncEntity, SyncCursor>()

        override suspend fun cursor(entity: SyncEntity): SyncCursor =
            cursors.getOrPut(entity) { SyncCursor(entity) }

        override suspend fun updateCursor(entity: SyncEntity, update: SyncCursor.() -> SyncCursor) {
            cursors[entity] = cursor(entity).update()
        }

        override suspend fun recordFailure(entity: SyncEntity, cause: Throwable) {
            failures += entity to cause::class.simpleName
        }
    }

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

    private object NoopAnalytics : AnalyticsTracker {
        override fun identify(traits: UserTraits) = Unit
        override fun track(eventName: String, properties: Map<String, Any?>) = Unit
        override fun setConsent(status: ConsentStatus) = Unit
        override fun flush() = Unit
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            object : Span {
                override val spanId = "span-$name"
                override val traceId = traceId
                override val parentSpanId = parentSpanId
                override val name = name
                override fun setAttribute(key: String, value: Any?): Span = this
            }

        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }
}
