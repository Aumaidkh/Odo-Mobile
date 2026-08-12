package com.hopcape.odo.infrastructure.firebase.performance

// ─────────────────────────────────────────────────────────────
// FirebaseTraceSanitizer — brings an arbitrary Span into line with
// Firebase Performance's own constraints on trace-name format,
// attribute count, and string lengths. Pure and SDK-free, so it is
// fully testable without a live Firebase project; every drop is
// reported through onDiagnostic rather than failing deep inside the
// vendor SDK. Mirrors :infrastructure:firebase:analytics's
// FirebaseEventSanitizer.
//
// The limits below are Firebase's documented constraints, not
// enforced by the SDK itself before the call is made.
// ─────────────────────────────────────────────────────────────
internal class FirebaseTraceSanitizer(
    private val onDiagnostic: (String) -> Unit = {},
) {

    /** Sanitizes a trace name, or null (reported) if it doesn't fit Firebase's rules. */
    fun sanitizeName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
            onDiagnostic("firebase-perf: dropped trace '$name' — invalid name length")
            return null
        }
        if (trimmed.startsWith("_")) {
            onDiagnostic("firebase-perf: dropped trace '$name' — leading underscore")
            return null
        }
        return trimmed
    }

    /** Drops attributes beyond Firebase's per-trace cap and truncates over-long values. */
    fun sanitizeAttributes(attributes: Map<String, String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for ((key, value) in attributes) {
            if (result.size >= MAX_ATTRIBUTES) {
                onDiagnostic("firebase-perf: exceeds $MAX_ATTRIBUTES attributes — dropping '$key'")
                break
            }
            val sanitizedKey = sanitizeAttributeName(key) ?: continue
            result[sanitizedKey] = value.take(MAX_ATTRIBUTE_VALUE_LENGTH)
        }
        return result
    }

    private fun sanitizeAttributeName(name: String): String? {
        if (name.isEmpty() || name.length > MAX_ATTRIBUTE_NAME_LENGTH) {
            onDiagnostic("firebase-perf: dropped attribute '$name' — invalid name length")
            return null
        }
        if (name.startsWith("_") || RESERVED_PREFIXES.any { name.startsWith(it) }) {
            onDiagnostic("firebase-perf: dropped attribute '$name' — reserved name")
            return null
        }
        return name
    }

    private companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_ATTRIBUTES = 5
        const val MAX_ATTRIBUTE_NAME_LENGTH = 40
        const val MAX_ATTRIBUTE_VALUE_LENGTH = 100
        val RESERVED_PREFIXES = listOf("google_", "firebase_", "ga_")
    }
}
