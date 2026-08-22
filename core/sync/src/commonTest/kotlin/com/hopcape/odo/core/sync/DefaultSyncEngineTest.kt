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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The engine's jobs: run the entities in dependency order, stop the **push** phase the moment
 * one refuses, and never let a refused **pull** stop the entities behind it.
 *
 * All three are correctness. Out-of-order pushes and carry-on-after-a-failed-push both end as
 * foreign-key errors on the server. Stopping a pull is the opposite mistake — it costs the
 * owner every entity after the one that failed, which is an account full of data rendering as
 * four first-run empty states (issue #312).
 */
class DefaultSyncEngineTest {

    @Test
    fun bothPhasesRunInDeclarationOrder_whateverOrderTheyWereRegisteredIn() = runTest {
        val ran = mutableListOf<Step>()
        // Deliberately shuffled: Koin hands them over in module-registration order, which is
        // not something the engine's correctness may depend on.
        val engine = engine(
            FakeSyncable(SyncEntity.DOCUMENTS, ran),
            FakeSyncable(SyncEntity.PROFILES, ran),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
            FakeSyncable(SyncEntity.CARS, ran),
        )

        assertEquals(SyncResult.Success, engine.sync())
        // Every push, then every pull. Pushing first is what keeps the pull's
        // last-write-wins comparison from overwriting an unsent local edit.
        assertEquals(
            listOf(
                PUSH to SyncEntity.PROFILES,
                PUSH to SyncEntity.CARS,
                PUSH to SyncEntity.SERVICE_LOGS,
                PUSH to SyncEntity.DOCUMENTS,
                PULL to SyncEntity.PROFILES,
                PULL to SyncEntity.CARS,
                PULL to SyncEntity.SERVICE_LOGS,
                PULL to SyncEntity.DOCUMENTS,
            ),
            ran,
        )
    }

    @Test
    fun aRefusedPushStopsThePushPhase_soChildrenOfAFailedParentAreNotSent() = runTest {
        val ran = mutableListOf<Step>()
        val engine = engine(
            FakeSyncable(SyncEntity.PROFILES, ran),
            FakeSyncable(SyncEntity.CARS, ran, pushAccepts = false),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
        )

        val result = engine.sync()

        assertIs<SyncResult.Partial>(result)
        assertEquals(SyncEntity.CARS, result.failedAt)
        // service_logs was never pushed. Every log referencing a car the server has not seen
        // would have failed anyway, turning one failure into a log full of consequences.
        assertEquals(
            listOf(PUSH to SyncEntity.PROFILES, PUSH to SyncEntity.CARS),
            ran.filter { it.first == PUSH },
        )
    }

    @Test
    fun aRefusedPushStillLetsEveryEntityPull() = runTest {
        // The two halves are independent. Rows that could not go up are no reason to leave
        // the owner without the rows that were waiting to come down.
        val ran = mutableListOf<Step>()
        val engine = engine(
            FakeSyncable(SyncEntity.PROFILES, ran, pushAccepts = false),
            FakeSyncable(SyncEntity.CARS, ran),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
        )

        engine.sync()

        assertEquals(
            listOf(
                PULL to SyncEntity.PROFILES,
                PULL to SyncEntity.CARS,
                PULL to SyncEntity.SERVICE_LOGS,
            ),
            ran.filter { it.first == PULL },
        )
    }

    @Test
    fun aRefusedPullDoesNotStopTheEntitiesBehindIt() = runTest {
        // The regression behind issue #312. PROFILES is the first entity, so a profile that
        // would not sync used to mean cars, service logs and documents were never fetched —
        // an owner with a full account seeing the first-run empty state on every screen.
        val ran = mutableListOf<Step>()
        val engine = engine(
            FakeSyncable(SyncEntity.PROFILES, ran, pullAccepts = false),
            FakeSyncable(SyncEntity.CARS, ran),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
        )

        val result = engine.sync()

        assertEquals(
            listOf(
                PULL to SyncEntity.PROFILES,
                PULL to SyncEntity.CARS,
                PULL to SyncEntity.SERVICE_LOGS,
            ),
            ran.filter { it.first == PULL },
        )
        // Still reported, so the scheduler retries what failed.
        assertIs<SyncResult.Partial>(result)
        assertEquals(SyncEntity.PROFILES, result.failedAt)
    }

    @Test
    fun theFirstFailureIsTheOneReported_evenWhenALaterEntityAlsoFails() = runTest {
        // A later failure is as likely to be a consequence as a cause, so the run names
        // where to start looking rather than where it happened to stop.
        val ran = mutableListOf<Step>()
        val engine = engine(
            FakeSyncable(SyncEntity.CARS, ran, pullAccepts = false),
            FakeSyncable(SyncEntity.DOCUMENTS, ran, pullAccepts = false),
        )

        val result = engine.sync()

        assertIs<SyncResult.Partial>(result)
        assertEquals(SyncEntity.CARS, result.failedAt)
    }

    @Test
    fun aSyncableThatThrowsIsTreatedAsARefusalAndRecorded() = runTest {
        val ran = mutableListOf<Step>()
        val synchronizer = RecordingSynchronizer()
        val boom = IllegalStateException("connection reset")
        val engine = engine(
            FakeSyncable(SyncEntity.PROFILES, ran),
            FakeSyncable(SyncEntity.CARS, ran, pushThrows = boom),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
            synchronizer = synchronizer,
        )

        val result = engine.sync()

        assertIs<SyncResult.Partial>(result)
        assertEquals(SyncEntity.CARS, result.failedAt)
        assertEquals(boom, result.cause)
        val expected: List<Pair<SyncEntity, String?>> = listOf(SyncEntity.CARS to "IllegalStateException")
        assertEquals(expected, synchronizer.failures)
    }

    @Test
    fun aGateThatSaysNoSessionSkipsWithoutAskingForARetry() = runTest {
        val ran = mutableListOf<Step>()
        val engine = engine(
            FakeSyncable(SyncEntity.CARS, ran),
            gate = { SyncVerdict.NoSession("not signed in") },
        )

        val result = engine.sync()

        assertIs<SyncResult.Skipped>(result)
        // Signing in is what changes this. Waking a worker until then helps nobody.
        assertFalse(result.retryable)
        // Not a single push attempted. An anonymous run can only collect 401s.
        assertTrue(ran.isEmpty())
    }

    @Test
    fun aGateThatCannotTellRightNowSkipsButAsksForARetry() = runTest {
        // The other half of issue #312. Both refusals used to be one boolean, so a run
        // refused because a token would not refresh was filed as done and dropped — and
        // when that run was the one triggered by signing in, the initial pull went with it.
        val engine = engine(
            FakeSyncable(SyncEntity.CARS, mutableListOf()),
            gate = { SyncVerdict.Unavailable("session held but no usable token") },
        )

        val result = engine.sync()

        assertIs<SyncResult.Skipped>(result)
        assertTrue(result.retryable)
    }

    @Test
    fun noRegisteredSyncablesIsSkipped_notSuccess() = runTest {
        // Reporting success for a run that did nothing would make an unwired graph look
        // healthy — the exact failure the debug row exists to catch. Not retryable: no
        // amount of waiting registers a syncable.
        val result = engine().sync()

        assertIs<SyncResult.Skipped>(result)
        assertFalse(result.retryable)
    }

    @Test
    fun everyEntityRunsWhenAllAccept() = runTest {
        val ran = mutableListOf<Step>()
        val engine = engine(
            FakeSyncable(SyncEntity.PROFILES, ran),
            FakeSyncable(SyncEntity.CARS, ran),
            FakeSyncable(SyncEntity.SERVICE_LOGS, ran),
            FakeSyncable(SyncEntity.OVERCHARGE_REPORTS, ran),
            FakeSyncable(SyncEntity.DOCUMENTS, ran),
            FakeSyncable(SyncEntity.HEALTH_SCORES, ran),
        )

        assertEquals(SyncResult.Success, engine.sync())
        assertEquals(12, ran.size)
    }

    @Test
    fun theRunningFlagIsClearedHoweverTheRunEnds() = runTest {
        val states = mutableListOf<Boolean>()
        val observer = SyncRunObserver { states += it }

        // A refusal still has to clear it, and a spinner left running is a bug report.
        engine(
            FakeSyncable(SyncEntity.CARS, mutableListOf(), pushAccepts = false),
            observer = observer,
        ).sync()

        assertEquals(listOf(true, false), states)
    }

    @Test
    fun aSkippedRunNeverClaimsToBeRunning() = runTest {
        val states = mutableListOf<Boolean>()
        engine(
            FakeSyncable(SyncEntity.CARS, mutableListOf()),
            gate = { SyncVerdict.NoSession("not signed in") },
            observer = SyncRunObserver { states += it },
        ).sync()

        assertTrue(states.isEmpty())
    }

    /* ------------------------------ scaffolding ------------------------------ */

    private fun engine(
        vararg syncables: Syncable,
        synchronizer: Synchronizer = RecordingSynchronizer(),
        gate: SyncGate = SyncGate { SyncVerdict.Allowed },
        observer: SyncRunObserver = SyncRunObserver { },
    ): SyncEngine = DefaultSyncEngine(
        syncables = syncables.toList(),
        synchronizer = synchronizer,
        telemetry = SyncTelemetry(NoopLogger, NoopAnalytics, NoopTracer, NoopCrash),
        gate = gate,
        observer = observer,
    )

    /** Which half of the run touched which entity, in the order it happened. */
    private class FakeSyncable(
        override val entity: SyncEntity,
        private val ran: MutableList<Step>,
        private val pushAccepts: Boolean = true,
        private val pullAccepts: Boolean = true,
        private val pushThrows: Throwable? = null,
        private val pullThrows: Throwable? = null,
    ) : Syncable {

        override suspend fun pushTo(synchronizer: Synchronizer): Boolean {
            ran += PUSH to entity
            pushThrows?.let { throw it }
            return pushAccepts
        }

        override suspend fun pullFrom(synchronizer: Synchronizer): Boolean {
            ran += PULL to entity
            pullThrows?.let { throw it }
            return pullAccepts
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

private typealias Step = Pair<String, SyncEntity>

private const val PUSH = "push"
private const val PULL = "pull"
