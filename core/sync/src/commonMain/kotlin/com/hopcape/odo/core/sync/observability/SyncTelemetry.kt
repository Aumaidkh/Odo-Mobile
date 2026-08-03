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

    /** Run [block] inside a child span of [parent], named for the entity being synced. */
    suspend fun <T> entity(entity: SyncEntity, parent: Span, block: suspend () -> T): T {
        val span = tracer.startSpan(
            name = "$TAG.${entity.name.lowercase()}",
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

    /** A run that never started, because nothing may be synced yet. */
    suspend fun skipped(reason: String) {
        logger.info(TAG, EVENT_RUN_SKIPPED, tc = currentTraceContext().toLog(), fields = mapOf(Key.REASON to reason))
    }

    /** How many rows an entity moved. The number that says whether sync is actually working. */
    suspend fun moved(entity: SyncEntity, pushed: Int, pulled: Int) {
        logger.info(
            TAG,
            EVENT_ENTITY_MOVED,
            tc = currentTraceContext().toLog(),
            fields = mapOf(Key.ENTITY to entity.name, Key.PUSHED to pushed, Key.PULLED to pulled),
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
    }

    companion object {
        const val TAG = "sync"

        /* Event names. Once shipped these are what the dashboard queries — do not rename. */
        const val EVENT_RUN_STARTED = "sync_started"
        const val EVENT_RUN_COMPLETED = "sync_completed"
        const val EVENT_RUN_FAILED = "sync_failed"
        const val EVENT_RUN_SKIPPED = "sync_skipped"
        const val EVENT_ENTITY_MOVED = "sync_entity_moved"
        const val EVENT_CONFLICT_RESOLVED = "sync_conflict_resolved"
        const val EVENT_ROWS_REFUSED = "sync_rows_refused"
        const val EVENT_IDENTITY_ADOPTED = "sync_identity_adopted"

        const val UNTRACED = "untraced"
        const val UNKNOWN = "Unknown"
        const val LOCAL = "local"
        const val REMOTE = "remote"
    }
}
