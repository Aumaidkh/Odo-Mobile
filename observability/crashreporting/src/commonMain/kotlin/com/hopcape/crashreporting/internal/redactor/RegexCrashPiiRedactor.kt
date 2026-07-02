package com.hopcape.crashreporting.internal.redactor

import com.hopcape.crashreporting.internal.model.Breadcrumb
import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// RegexCrashPiiRedactor — self-contained sibling of the logging
// module's RegexPiiRedactor, using the same email/phone patterns. It
// can't reuse that class directly: it's `internal` to
// :observability:logging and typed to LogEvent, and modules don't
// import each other's internals. A local copy keeps this module
// self-contained (as the other observability modules are).
//
// Scrubs the exception message, every breadcrumb message, and any
// String value in customKeys. Non-String custom values pass through
// untouched (numbers/booleans aren't PII carriers here).
// ─────────────────────────────────────────────────────────────
internal class RegexCrashPiiRedactor(
    private val patterns: Map<String, Regex> = mapOf(
        "email" to Regex("""[\w.+-]+@[\w-]+\.[\w.-]+"""),
        "phone" to Regex("""\b\d{10}\b"""),
    ),
) : CrashPiiRedactor {

    override fun redact(report: CrashReport): CrashReport = report.copy(
        throwableMessage = report.throwableMessage?.let(::mask),
        breadcrumbs = report.breadcrumbs.map(::redactCrumb),
        customKeys = report.customKeys.mapValues { (_, v) -> if (v is String) mask(v) else v },
    )

    private fun redactCrumb(crumb: Breadcrumb): Breadcrumb =
        crumb.copy(message = mask(crumb.message))

    private fun mask(input: String): String {
        var result = input
        patterns.forEach { (name, regex) ->
            result = regex.replace(result) { "***${name}_masked***" }
        }
        return result
    }
}
