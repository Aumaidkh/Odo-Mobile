package com.hopcape.performance.api

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// ─────────────────────────────────────────────────────────────
// TraceContext — correlation identifiers that stitch related
// spans/logs together: a [sessionId] (per app session), a [flowId]
// (a user journey, e.g. "shopping"), and a [traceId] (a single
// attempt/operation within that flow, e.g. one checkout).
//
// Unlike the logging module's plain-data TraceContext, this one IS
// a CoroutineContext.Element, so it *propagates automatically* down
// the coroutine tree:
//
//     viewModelScope.launch(trace) {          // install it once
//         repo.login(...)                      // deep inside...
//     }
//     // ...repo reads it back without threading it through params:
//     val trace = currentTraceContext()
//
// This is what lets a repository open a nested span on the SAME
// trace the ViewModel started, without passing the id by hand
// through every function signature.
// ─────────────────────────────────────────────────────────────
class TraceContext(
    val sessionId: String? = null,
    val flowId: String? = null,
    val traceId: String? = null,
) : AbstractCoroutineContextElement(TraceContext) {

    /** The key under which a [TraceContext] is stored in a [CoroutineContext]. */
    companion object Key : CoroutineContext.Key<TraceContext>

    /** Derive a child context with a new [traceId] but the same session/flow. */
    fun withNewTrace(traceId: String): TraceContext =
        TraceContext(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /**
     * Derive a child context that starts a new [flowId]. The [traceId] resets to
     * [traceId] (null by default) — a new flow begins without an active trace
     * until one is explicitly started.
     */
    fun withNewFlow(flowId: String, traceId: String? = null): TraceContext =
        TraceContext(sessionId = sessionId, flowId = flowId, traceId = traceId)

    /** Value equality (the coroutine-element supertype is identity-based). */
    override fun equals(other: Any?): Boolean =
        other is TraceContext &&
            sessionId == other.sessionId &&
            flowId == other.flowId &&
            traceId == other.traceId

    override fun hashCode(): Int {
        var result = sessionId?.hashCode() ?: 0
        result = 31 * result + (flowId?.hashCode() ?: 0)
        result = 31 * result + (traceId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "TraceContext(sessionId=$sessionId, flowId=$flowId, traceId=$traceId)"
}

/**
 * Reads the [TraceContext] installed on the calling coroutine, or an empty one if
 * none was set. Suspend because it inspects the ambient [coroutineContext] — the
 * companion to `launch(trace) { ... }`.
 */
suspend fun currentTraceContext(): TraceContext =
    coroutineContext[TraceContext] ?: TraceContext()
