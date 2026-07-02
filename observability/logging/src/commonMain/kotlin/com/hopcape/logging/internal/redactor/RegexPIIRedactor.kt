package com.hopcape.logging.internal.redactor

import com.hopcape.logging.internal.model.LogEvent

internal class RegexPiiRedactor(
    private val patterns: Map<String, Regex> = mapOf(
        "email" to Regex("""[\w.+-]+@[\w-]+\.[\w.-]+"""),
        "phone" to Regex("""\b\d{10}\b""")
    )
) : PiiRedactor {
    override fun redact(event: LogEvent): LogEvent {
        val redactedFields = event.fields.mapValues { (_, value) ->
            if (value is String) maskString(value) else value
        }
        return event.copy(fields = redactedFields)
    }

    private fun maskString(input: String): String {
        var result = input
        patterns.forEach { (name, regex) ->
            result = regex.replace(result) { "***${name}_masked***" }
        }
        return result
    }
}