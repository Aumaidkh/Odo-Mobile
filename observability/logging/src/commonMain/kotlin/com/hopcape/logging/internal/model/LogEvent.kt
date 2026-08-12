package com.hopcape.logging.internal.model

import com.hopcape.logging.api.LogLevel
import kotlin.time.Clock

internal data class LogEvent(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val event: String,
    val sessionId: String? = null,
    val flowId: String? = null,
    val traceId: String? = null,
    val fields: Map<String, Any?> = emptyMap()
) {
    class Builder(private val tag: String, private val event: String) {
        private var level: LogLevel = LogLevel.INFO
        private var sessionId: String? = null
        private var flowId: String? = null
        private var traceId: String? = null
        private val fields = mutableMapOf<String, Any?>()

        fun level(level: LogLevel) = apply { this.level = level }
        fun sessionId(id: String?) = apply { this.sessionId = id }
        fun flowId(id: String?) = apply { this.flowId = id }
        fun traceId(id: String?) = apply { this.traceId = id }
        fun field(key: String, value: Any?) = apply { fields[key] = value }
        fun fields(map: Map<String, Any?>) = apply { fields.putAll(map) }

        fun build(): LogEvent = LogEvent(
            timestampMs = Clock.System.now().toEpochMilliseconds(),
            level = level,
            tag = tag,
            event = event,
            sessionId = sessionId,
            flowId = flowId,
            traceId = traceId,
            fields = fields.toMap()
        )
    }
}