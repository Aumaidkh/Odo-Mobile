package com.hopcape.analytics.api

import com.hopcape.analytics.internal.validation.EventRegistry
import com.hopcape.analytics.internal.validation.SchemaValidationResult

/**
 * Why strict validation would drop or reject [eventName] with [properties], plus any non-null
 * property the schema does not declare. Empty when the event is fully declared. For tests.
 */
fun List<EventSchema>.problemsWith(eventName: String, properties: Map<String, Any?>): List<String> {
    val schema = firstOrNull { it.eventName == eventName }
        ?: return listOf("Event '$eventName' is not declared")
    val undeclared = properties.filterValues { it != null }.keys - schema.requiredProperties.keys - schema.optionalProperties.keys
    val invalid = when (val result = EventRegistry(listOf(schema)).validate(eventName, properties)) {
        is SchemaValidationResult.Invalid -> result.reasons
        else -> emptyList()
    }
    return invalid + undeclared.map { "Property '$it' of event '$eventName' is not declared" }
}
