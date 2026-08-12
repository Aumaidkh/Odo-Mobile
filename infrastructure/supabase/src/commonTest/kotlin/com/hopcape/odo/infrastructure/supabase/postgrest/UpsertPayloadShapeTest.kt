package com.hopcape.odo.infrastructure.supabase.postgrest

import com.hopcape.odo.infrastructure.supabase.MockResponse
import com.hopcape.odo.infrastructure.supabase.SupabaseTestHarness
import com.hopcape.odo.infrastructure.supabase.bodyText
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shape of an upsert body.
 *
 * PostgREST requires every object in a bulk insert to carry the **same keys** and rejects the
 * whole array with `PGRST102: All object keys must match` otherwise. A codec that drops null
 * properties makes the key set depend on each row's data, so a batch fails as soon as two rows
 * differ in which optional fields are set — while a batch of one always works, which is what
 * lets it reach production.
 */
class UpsertPayloadShapeTest {

    @Serializable
    private data class Row(
        @SerialName("id") val id: String,
        @SerialName("variant") val variant: String? = null,
        @SerialName("nickname") val nickname: String? = null,
    )

    @Test
    fun everyRowInABatchCarriesTheSameKeys() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }

        harness.postgrest.upsert(
            table = "cars",
            serializer = Row.serializer(),
            rows = listOf(
                Row(id = "a", variant = "VXI", nickname = "Swifty"),
                Row(id = "b"),
                Row(id = "c", variant = "Smart"),
            ),
            returnRows = false,
        )

        val keysPerRow = harness.onlyRequest().bodyText().keySetsPerObject()
        assertEquals(3, keysPerRow.size, keysPerRow.toString())
        assertEquals(1, keysPerRow.distinct().size, "rows disagree on keys: $keysPerRow")
    }

    @Test
    fun aNullIsWrittenOutRatherThanDropped() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }

        harness.postgrest.upsert(
            table = "cars",
            serializer = Row.serializer(),
            rows = listOf(Row(id = "a")),
            returnRows = false,
        )

        // An owner who clears a nickname needs the clearing to reach the server. An omitted
        // key reads as "leave it alone", which would keep the old value there for good.
        val body = harness.onlyRequest().bodyText()
        assertTrue(body.contains("\"variant\":null"), body)
        assertTrue(body.contains("\"nickname\":null"), body)
    }

    /* ------------------------------ scaffolding ------------------------------ */

    /**
     * The key names of each top-level object in the array, in order.
     *
     * Counts braces rather than parsing, which is enough for these flat rows and keeps the
     * assertion about the bytes actually sent.
     */
    private fun String.keySetsPerObject(): List<List<String>> =
        Regex("""\{[^{}]*}""").findAll(this)
            .map { match ->
                Regex(""""([^"]+)"\s*:""").findAll(match.value)
                    .map { it.groupValues[1] }
                    .sorted()
                    .toList()
            }
            .toList()
}
