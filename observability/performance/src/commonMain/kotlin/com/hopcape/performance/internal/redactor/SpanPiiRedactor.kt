package com.hopcape.performance.internal.redactor

// ─────────────────────────────────────────────────────────────
// SpanPiiRedactor — separate abstraction (ISP: not part of the
// SpanSink contract) so redaction can be tested and swapped
// independently. Mirrors the logging module's PiiRedactor.
// ─────────────────────────────────────────────────────────────
internal interface SpanPiiRedactor {
    /** Redacts a single attribute value before it reaches any sink. */
    fun redact(value: String): String
}
