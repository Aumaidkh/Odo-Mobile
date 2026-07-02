package com.hopcape.analytics.internal.validation

import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EventRegistryTest {

    private val registry = EventRegistry(
        listOf(
            EventSchema(
                eventName = "bill_scanned",
                requiredProperties = mapOf(
                    "odometer" to PropertyType.INT,
                    "workshop" to PropertyType.STRING,
                ),
            ),
        ),
    )

    @Test
    fun unknownEvent_isUnregistered() {
        assertEquals(SchemaValidationResult.Unregistered, registry.validate("mystery", emptyMap()))
    }

    @Test
    fun validEvent_passes() {
        val result = registry.validate("bill_scanned", mapOf("odometer" to 42_000, "workshop" to "auto-care"))
        assertEquals(SchemaValidationResult.Valid, result)
    }

    @Test
    fun missingRequiredProperty_isInvalid_withReason() {
        val result = registry.validate("bill_scanned", mapOf("odometer" to 42_000))
        assertIs<SchemaValidationResult.Invalid>(result)
        assertTrue(result.reasons.any { it.contains("workshop") }, "reason should name the missing property")
    }

    @Test
    fun wrongTypedProperty_isInvalid_withReason() {
        val result = registry.validate("bill_scanned", mapOf("odometer" to "not-a-number", "workshop" to "x"))
        assertIs<SchemaValidationResult.Invalid>(result)
        assertTrue(result.reasons.any { it.contains("odometer") })
    }

    @Test
    fun emptyRegistry_treatsEverythingAsUnregistered() {
        val empty = EventRegistry(emptyList())
        assertEquals(SchemaValidationResult.Unregistered, empty.validate("anything", mapOf("a" to 1)))
    }
}
