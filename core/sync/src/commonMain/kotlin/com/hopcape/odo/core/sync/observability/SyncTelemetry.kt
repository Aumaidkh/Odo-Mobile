package com.hopcape.odo.core.sync.observability

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import com.hopcape.performance.api.currentTraceContext
import com.hopcape.logging.api.TraceContext as LogTrace
import com.hopcape.performance.api.TraceContext as PerfTrace

/**
 * Observability for a sync run, behind one intent-named surface.
 *
 * Sync is invisible when it works and infuriating when it doesn't, which is why this exists
 * from the engine's first commit rather than being added after the first support ticket
 * (SYNC_DESIGN §11). A run reports: which entities moved how many rows, how long it took,
 * and how it ended.
 *
 * **Never log row contents.** Entity names, counts, durations and error *type* names only —
 * these tables are the owner's service history and papers.
 *
 * Fire-and-forget by contract: nothing here returns a decision, and [entity] hands back its
 * block's result untouched, so instrumentation can never change what a run does.
 */
class SyncTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val tracer: PerformanceTracer,
    private val crash: CrashRecorder,
) {

    /**
     * Span the whole run and return the root span, so each entity can hang beneath it.
     *
     * The caller closes it via [endRun]. A run is not a `block { }` like the per-entity
     * spans because the engine needs the span while deciding its result.
     */
    suspend fun startRun(): Span {
        val trace = currentTraceContext()
        logger.info(TAG, EVENT_RUN_STARTED, tc = trace.toLog())
        return tracer.startSpan(name = "$TAG.run", traceId = trace.traceId ?: UNTRACED)
    }

    /**
     * Run [block] inside a child span of [parent], named for the entity and the half of the
     * run being attempted.
     *
     * [phase] is part of the name because an entity now gets two turns per run, and a span
     * that could be either is a span nobody can read.
     */
    suspend fun <T> entity(entity: SyncEntity, phase: String, parent: Span, block: suspend () -> T): T {
        val span = tracer.startSpan(
            name = "$TAG.$phase.${entity.name.lowercase()}",
            traceId = parent.traceId,
            parentSpanId = parent.spanId,
        )
        return try {
            block()
        } finally {
            tracer.endSpan(span)
        }
    }

    /** A run that finished with every entity accepted. */
    suspend fun completed(run: Span, entities: Int, millis: Long) {
        tracer.endSpan(run)
        logger.info(
            TAG,
            EVENT_RUN_COMPLETED,
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.ENTITIES to entities, Key.DURATION_MS to millis),
        )
        analytics.track(EVENT_RUN_COMPLETED, mapOf(Key.ENTITIES to entities, Key.DURATION_MS to millis))
    }

    /**
     * A run that stopped at [entity]. The rest of the list is deliberately not attempted —
     * pushing children whose parent failed can only produce foreign-key errors.
     */
    suspend fun stopped(run: Span, entity: SyncEntity, cause: Throwable?, millis: Long) {
        cause?.let { crash.recordNonFatal(it, mapOf(Key.ENTITY to entity.name)) }
        tracer.endSpan(run)
        logger.error(
            TAG,
            EVENT_RUN_FAILED,
            tc = currentTraceContext().toLog(),
            fields = buildMap {
                put(Key.ENTITY, entity.name)
                put(Key.DURATION_MS, millis)
                cause?.let { put(Key.ERROR, it::class.simpleName) }
            },
        )
        analytics.track(
            EVENT_RUN_FAILED,
            mapOf(Key.ENTITY to entity.name, Key.ERROR to (cause?.let { it::class.simpleName } ?: UNKNOWN)),
        )
    }

    /**
     * A run that never started.
     *
     * [retryable] is recorded because the two kinds of skip look identical in a log and are
     * not the same thing at all: one is an install waiting to be signed into, the other is a
     * run that was lost. Telling them apart in the log is how the second stops being
     * invisible (issue #312).
     */
    suspend fun skipped(reason: String, retryable: Boolean) {
        logger.info(
            TAG,
            EVENT_RUN_SKIPPED,
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.REASON to reason, Key.RETRYABLE to retryable),
        )
    }

    /**
     * Somebody asked for a run.
     *
     * Logged at the scheduler rather than the engine, and deliberately the only thing here
     * that is not `suspend`: [com.hopcape.odo.core.sync.SyncScheduler] is a plain interface
     * called from ordinary code, and a trace context is not worth making it suspend for.
     *
     * It exists so a missing [EVENT_RUN_STARTED] can be read. Without it, a worker that was
     * enqueued and never ran and a worker that was never enqueued produce exactly the same
     * log — which is to say, nothing.
     */
    fun requested(reason: String) {
        logger.info(TAG, EVENT_RUN_REQUESTED, fields = mapOf(Key.REASON to reason))
    }

    /**
     * What the platform scheduler currently thinks of the pending run.
     *
     * A job parked on an unmet constraint is indistinguishable from a job that was never
     * created, from inside the app. This is the line that separates them.
     */
    fun workState(state: String, attempts: Int) {
        logger.info(TAG, EVENT_WORK_STATE, fields = mapOf(Key.STATE to state, Key.ATTEMPTS to attempts))
    }

    /**
     * A fetch that could not say what to fetch — no owner id, no car id, a placeholder.
     *
     * This used to be a silent `return emptyList()`, which the runner could not tell from a
     * server that genuinely had nothing new. That is the shape of the bug in issue #312: a
     * run that pulled nothing, reported success, and was dropped. Warned about rather than
     * logged at info, because it is never normal on a signed-in install.
     */
    suspend fun scopeMissing(entity: SyncEntity, key: String) {
        val fields = mapOf(Key.ENTITY to entity.name, Key.SCOPE_KEY to key)
        logger.warn(TAG, EVENT_SCOPE_MISSING, tc = currentTraceContext().toLog(), fields = fields)
        analytics.track(EVENT_SCOPE_MISSING, fields)
    }

    /**
     * How many rows an entity moved. The number that says whether sync is actually working.
     *
     * Reported once per phase rather than once per entity, because the two halves no longer
     * finish together. The event name is unchanged and both fields are always present — a
     * push reports `pulled = 0` and a pull reports `pushed = 0` — so a dashboard summing
     * either column still gets the same totals it always did. [Key.PHASE] is what tells the
     * two lines apart.
     */
    suspend fun moved(entity: SyncEntity, phase: String, pushed: Int = 0, pulled: Int = 0) {
        logger.info(
            TAG,
            EVENT_ENTITY_MOVED,
            tc = currentTraceContext().toLog(),
            fields = mapOf(
                Key.ENTITY to entity.name,
                Key.PHASE to phase,
                Key.PUSHED to pushed,
                Key.PULLED to pulled,
            ),
        )
    }

    /**
     * Rows the server refused for good, taken out of the outbox so the run can carry on.
     *
     * A warning and an event rather than a silent skip: these rows are the owner's records
     * and they are now not going to reach the server without help. [status] is the HTTP code
     * the server answered with — never the row, never the constraint's value.
     */
    suspend fun refused(entity: SyncEntity, count: Int, status: Int) {
        val fields = mapOf(Key.ENTITY to entity.name, Key.REFUSED to count, Key.STATUS to status)
        logger.warn(TAG, EVENT_ROWS_REFUSED, tc = currentTraceContext().toLog(), fields = fields)
        analytics.track(EVENT_ROWS_REFUSED, fields)
    }

    /**
     * Local rows that took on the server's identity before being pushed — a car re-added
     * after a reinstall, carrying a fresh id for a plate the server already holds.
     *
     * Reported because the row's primary key changed and its children moved with it. That is
     * the kind of thing nobody believes happened until they can see it in a log. It also
     * counts reinstalls, which is worth knowing on its own.
     */
    suspend fun identityAdopted(entity: SyncEntity, count: Int) {
        val fields = mapOf(Key.ENTITY to entity.name, Key.ADOPTED to count)
        logger.warn(TAG, EVENT_IDENTITY_ADOPTED, tc = currentTraceContext().toLog(), fields = fields)
        analytics.track(EVENT_IDENTITY_ADOPTED, fields)
    }

    /**
     * The device asserted its primary flag before a push: whatever other car the server held
     * as primary was demoted to make room, or there was nothing to demote.
     *
     * A log line, not an analytics event — it fires on every push that carries a primary
     * car, and the interesting cases (a reinstall reclaiming the flag) are already counted
     * by [identityAdopted] and visible in the server's row history.
     */
    suspend fun primaryReclaimed(entity: SyncEntity) {
        logger.info(
            TAG,
            EVENT_PRIMARY_RECLAIMED,
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.ENTITY to entity.name),
        )
    }

    /**
     * A last-write-wins resolution. Always reported: the losing side is never silently
     * discarded, so a conflict storm shows up as a spike rather than as a mystery
     * (SYNC_DESIGN §7).
     */
    suspend fun conflictResolved(entity: SyncEntity, localWon: Boolean) {
        logger.warn(
            TAG,
            EVENT_CONFLICT_RESOLVED,
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.ENTITY to entity.name, Key.WINNER to if (localWon) LOCAL else REMOTE),
        )
        analytics.track(
            EVENT_CONFLICT_RESOLVED,
            mapOf(Key.ENTITY to entity.name, Key.WINNER to if (localWon) LOCAL else REMOTE),
        )
    }

    private fun PerfTrace.toLog(): LogTrace =
        LogTrace(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    object Key {
        const val ENTITY = "entity"
        const val ENTITIES = "entities"
        const val DURATION_MS = "duration_ms"
        const val ERROR = "error"
        const val REASON = "reason"
        const val PUSHED = "pushed"
        const val PULLED = "pulled"
        const val WINNER = "winner"
        const val REFUSED = "refused"
        const val STATUS = "status"
        const val ADOPTED = "adopted"
        const val PHASE = "phase"
        const val RETRYABLE = "retryable"
        const val SCOPE_KEY = "scope_key"
        const val STATE = "state"
        const val ATTEMPTS = "attempts"
    }

    companion object {
        const val TAG = "sync"

        /* Event names. Once shipped these are what the dashboard queries — do not rename. */
        const val EVENT_RUN_STARTED = "sync_started"
        const val EVENT_RUN_COMPLETED = "sync_completed"
        const val EVENT_RUN_FAILED = "sync_failed"
        const val EVENT_RUN_SKIPPED = "sync_skipped"
        const val EVENT_RUN_REQUESTED = "sync_requested"
        const val EVENT_WORK_STATE = "sync_work_state"
        const val EVENT_SCOPE_MISSING = "sync_scope_missing"
        const val EVENT_ENTITY_MOVED = "sync_entity_moved"
        const val EVENT_CONFLICT_RESOLVED = "sync_conflict_resolved"
        const val EVENT_ROWS_REFUSED = "sync_rows_refused"
        const val EVENT_IDENTITY_ADOPTED = "sync_identity_adopted"
        const val EVENT_PRIMARY_RECLAIMED = "sync_primary_reclaimed"

        const val UNTRACED = "untraced"
        const val UNKNOWN = "Unknown"
        const val LOCAL = "local"
        const val REMOTE = "remote"
    }
}
