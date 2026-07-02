package com.hopcape.analytics.api

// ─────────────────────────────────────────────────────────────
// EventSchema — the contract for one event type: its name plus
// the properties it must carry and their expected types. This is
// the single source of truth that stops "a PM dashboard silently
// breaks because an engineer typo'd a property name or shipped the
// wrong type". Callers declare their taxonomy up front and pass it
// via AnalyticsConfig.events.
//
// Type checking is expressed with the KMP-safe [PropertyType] enum
// rather than JVM reflection (`Class<*>`), so the same schema works
// unchanged on Android and iOS.
// ─────────────────────────────────────────────────────────────
data class EventSchema(
    val eventName: String,
    val requiredProperties: Map<String, PropertyType> = emptyMap(),
)

/** The value types an event property can be constrained to. */
enum class PropertyType {
    STRING,
    INT,
    LONG,
    DOUBLE,
    BOOLEAN,
    NUMBER,
    ANY;

    /** Whether [value] satisfies this type constraint. Internal — validation is a module concern. */
    internal fun matches(value: Any): Boolean = when (this) {
        STRING -> value is String
        INT -> value is Int
        LONG -> value is Long
        DOUBLE -> value is Double
        BOOLEAN -> value is Boolean
        NUMBER -> value is Number
        ANY -> true
    }
}
