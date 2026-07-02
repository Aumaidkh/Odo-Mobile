package com.hopcape.analytics.internal.validation

import com.hopcape.analytics.api.EventSchema

// ─────────────────────────────────────────────────────────────
// EventRegistry — the central taxonomy. Built once from the
// schemas supplied via AnalyticsConfig.events and then treated as
// immutable, so there is no shared mutable global state to leak
// between tests or across app sessions (a deliberate improvement
// over a mutable singleton). In a larger codebase the schema list
// is often generated from a checked-in spec shared with the data
// team; the runtime contract is still this lookup.
// ─────────────────────────────────────────────────────────────
internal class EventRegistry(schemas: List<EventSchema>) {

    private val schemasByName: Map<String, EventSchema> = schemas.associateBy { it.eventName }

    fun validate(name: String, properties: Map<String, Any?>): SchemaValidationResult {
        val schema = schemasByName[name] ?: return SchemaValidationResult.Unregistered

        val reasons = buildList {
            schema.requiredProperties.forEach { (propertyName, expectedType) ->
                val value = properties[propertyName]
                when {
                    value == null ->
                        add("Missing required property '$propertyName' for event '$name'")

                    !expectedType.matches(value) ->
                        add("Property '$propertyName' expected $expectedType, got ${value::class.simpleName}")
                }
            }
        }

        return if (reasons.isEmpty()) SchemaValidationResult.Valid
        else SchemaValidationResult.Invalid(reasons)
    }
}
