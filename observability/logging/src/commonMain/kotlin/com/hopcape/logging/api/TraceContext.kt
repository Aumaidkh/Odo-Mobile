package com.hopcape.logging.api

/**
 * Correlation identifiers carried alongside a log event so related lines can be
 * stitched together: a [sessionId] (per app session), a [flowId] (a user journey),
 * and a [traceId] (a single attempt/operation within that flow).
 */
data class TraceContext(
    val sessionId: String? = null,
    val flowId: String? = null,
    val traceId: String? = null
) {
    // Derive a child context with a new traceId but same session/flow.
    fun withNewTrace(traceId: String) = copy(traceId = traceId)
    fun withNewFlow(flowId: String, traceId: String? = null) = copy(flowId = flowId, traceId = traceId)
}
