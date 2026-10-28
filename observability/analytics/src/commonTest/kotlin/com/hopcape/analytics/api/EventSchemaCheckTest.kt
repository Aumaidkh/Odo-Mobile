package com.hopcape.analytics.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventSchemaCheckTest {

    private val schemas = listOf(
        EventSchema("shown", mapOf("step" to PropertyType.STRING), mapOf("variant" to PropertyType.STRING)),
    )

    @Test
    fun aDeclaredEventHasNoProblems() = assertTrue(schemas.problemsWith("shown", mapOf("step" to "car")).isEmpty())

    @Test
    fun anUndeclaredEventIsNamed() =
        assertEquals(listOf("Event 'hidden' is not declared"), schemas.problemsWith("hidden", emptyMap()))

    @Test
    fun anUndeclaredPropertyIsNamed() = assertEquals(
        listOf("Property 'extra' of event 'shown' is not declared"),
        schemas.problemsWith("shown", mapOf("step" to "car", "extra" to 1)),
    )

    @Test
    fun aMissingPropertyIsAProblem() = assertEquals(1, schemas.problemsWith("shown", emptyMap()).size)

    @Test
    fun anOptionalPropertyMayBeAbsentOrPresent() {
        assertTrue(schemas.problemsWith("shown", mapOf("step" to "car")).isEmpty())
        assertTrue(schemas.problemsWith("shown", mapOf("step" to "car", "variant" to "b")).isEmpty())
    }

    @Test
    fun anOptionalPropertyOfTheWrongTypeIsAProblem() =
        assertEquals(1, schemas.problemsWith("shown", mapOf("step" to "car", "variant" to 2)).size)
}
