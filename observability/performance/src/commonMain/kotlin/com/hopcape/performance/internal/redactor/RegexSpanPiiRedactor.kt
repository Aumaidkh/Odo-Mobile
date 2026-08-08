package com.hopcape.performance.internal.redactor

// ─────────────────────────────────────────────────────────────
// RegexSpanPiiRedactor — self-contained sibling of the logging
// module's RegexPiiRedactor and the crashreporting module's
// RegexCrashPiiRedactor, using the same email/phone patterns plus an
// Indian registration-plate pattern (this module's first egress path
// to a third party, so plate numbers — Odo's specific PII class —
// need their own rule; neither sibling redactor has it). Can't reuse
// either directly: both are `internal` to their own module and typed
// to that module's event model. A local copy keeps this module
// self-contained, as the other observability modules are.
//
// Applied to every attribute value a SpanSink receives, never to
// names/ids — those are call-site constants, not user data.
// ─────────────────────────────────────────────────────────────
internal class RegexSpanPiiRedactor(
    private val patterns: Map<String, Regex> = mapOf(
        "email" to Regex("""[\w.+-]+@[\w-]+\.[\w.-]+"""),
        "phone" to Regex("""\b\d{10}\b"""),
        "plate" to Regex("""\b[A-Z]{2}\s?\d{1,2}\s?[A-Z]{1,3}\s?\d{4}\b"""),
    ),
) : SpanPiiRedactor {

    override fun redact(value: String): String {
        var result = value
        patterns.forEach { (name, regex) ->
            result = regex.replace(result) { "***${name}_masked***" }
        }
        return result
    }
}
