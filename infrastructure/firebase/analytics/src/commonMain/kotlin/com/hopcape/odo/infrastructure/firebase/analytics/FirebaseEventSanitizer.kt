package com.hopcape.odo.infrastructure.firebase.analytics

// ─────────────────────────────────────────────────────────────
// FirebaseEventSanitizer — brings an arbitrary AnalyticsTracker.track()
// call in line with Firebase Analytics' own constraints on event/param
// names, string lengths, allowed value types, and param count. Pure and
// SDK-free, so it is fully testable without a live Firebase project;
// every drop or coercion is reported through onDiagnostic rather than
// thrown, matching the "a vendor SDK can never crash the host" guarantee
// the rest of the analytics pipeline holds.
//
// The limits below are Firebase's documented constraints, not enforced
// by the gitlive wrapper itself — this class is what enforces them.
// ─────────────────────────────────────────────────────────────
internal class FirebaseEventSanitizer(
    private val onDiagnostic: (String) -> Unit = {},
) {

    /** Sanitizes an event name and its properties, or null if the event name itself is unusable. */
    fun sanitizeEvent(name: String, properties: Map<String, Any?>): SanitizedEvent? {
        val sanitizedName = sanitizeName(name, MAX_EVENT_NAME_LENGTH)
        if (sanitizedName == null) {
            onDiagnostic("firebase: dropped event '$name' — invalid event name")
            return null
        }

        val parameters = LinkedHashMap<String, Any>()
        for ((key, value) in properties) {
            if (parameters.size >= MAX_PARAMS) {
                onDiagnostic("firebase: event '$name' exceeds $MAX_PARAMS params — dropping '$key'")
                continue
            }
            val sanitizedKey = sanitizeName(key, MAX_PARAM_NAME_LENGTH)
            if (sanitizedKey == null) {
                onDiagnostic("firebase: event '$name' dropped param '$key' — invalid param name")
                continue
            }
            val sanitizedValue = sanitizeValue(value)
            if (sanitizedValue == null) {
                onDiagnostic("firebase: event '$name' dropped param '$key' — null value")
                continue
            }
            parameters[sanitizedKey] = sanitizedValue
        }
        return SanitizedEvent(sanitizedName, parameters)
    }

    /** Sanitizes a user-property name, or null (reported) if it doesn't fit Firebase's rules. */
    fun sanitizeUserPropertyName(name: String): String? {
        val sanitized = sanitizeName(name, MAX_USER_PROPERTY_NAME_LENGTH)
        if (sanitized == null) onDiagnostic("firebase: dropped user property '$name' — invalid name")
        return sanitized
    }

    /** User-property values only have a length cap — truncated silently, same as a param string. */
    fun sanitizeUserPropertyValue(value: String): String = value.take(MAX_USER_PROPERTY_VALUE_LENGTH)

    /** Same name rule for events, params, and user properties — only the max length differs. */
    private fun sanitizeName(raw: String, maxLength: Int): String? {
        if (raw.length > maxLength) return null
        if (!NAME_PATTERN.matches(raw)) return null
        if (RESERVED_PREFIXES.any { raw.startsWith(it) }) return null
        return raw
    }

    /** Firebase event params accept String/Long/Double; everything else is coerced or dropped. */
    private fun sanitizeValue(value: Any?): Any? = when (value) {
        null -> null
        is String -> value.take(MAX_PARAM_VALUE_LENGTH)
        is Long -> value
        is Int -> value.toLong()
        is Double -> value
        is Float -> value.toDouble()
        is Boolean -> value.toString()
        else -> value.toString().take(MAX_PARAM_VALUE_LENGTH)
    }

    private companion object {
        val NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*$")
        val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")
        const val MAX_EVENT_NAME_LENGTH = 40
        const val MAX_PARAM_NAME_LENGTH = 40
        const val MAX_PARAM_VALUE_LENGTH = 100
        const val MAX_PARAMS = 25
        const val MAX_USER_PROPERTY_NAME_LENGTH = 24
        const val MAX_USER_PROPERTY_VALUE_LENGTH = 36
    }
}

/** An event name + params, already valid for Firebase's `logEvent`. */
internal data class SanitizedEvent(val name: String, val parameters: Map<String, Any>)
