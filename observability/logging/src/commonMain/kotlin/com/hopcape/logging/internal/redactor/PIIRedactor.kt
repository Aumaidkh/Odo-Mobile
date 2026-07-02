package com.hopcape.logging.internal.redactor

import com.hopcape.logging.internal.model.LogEvent

// ─────────────────────────────────────────────────────────────
// PiiRedactor — separate abstraction (ISP: not part of LogSink
// contract) so redaction logic can be tested/swapped independently
// ─────────────────────────────────────────────────────────────
internal interface PiiRedactor {
    fun redact(event: LogEvent): LogEvent
}