package com.hopcape.odo.infrastructure.database.servicelog

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The breakdown a scanned bill produced used to reach the database and stop there — no column
 * held it, so every entry read back had none. That emptied the detail card *and* silently
 * disabled the per-line fairness check, which falls back to a single lump sum without lines.
 */
class ServiceLogLineItemsTest {

    private fun line(label: String?, category: ServiceCategory, paise: Long) =
        ServiceLogLineItem.of(label, category, paise).getOrNull()!!

    @Test
    fun `a breakdown survives a round trip in the order it was printed`() {
        val items = listOf(
            line("Engine Oil Replacement", ServiceCategory.OIL_CHANGE, 280_000),
            line("Labour", ServiceCategory.GENERAL_SERVICE, 75_000),
        )

        val restored = items.toJson().toLineItems()

        assertEquals(items, restored)
    }

    @Test
    fun `a label keeps its commas, which is why this is not a GROUP_CONCAT column`() {
        val items = listOf(line("Oil, filter, and labour", ServiceCategory.OIL_CHANGE, 280_000))

        assertEquals(items, items.toJson().toLineItems())
    }

    @Test
    fun `an entry with no breakdown stores nothing rather than an empty list`() {
        assertNull(emptyList<ServiceLogLineItem>().toJson())
        assertTrue(null.toLineItems().isEmpty())
    }

    @Test
    fun `a line with no label of its own still comes back`() {
        val items = listOf(line(null, ServiceCategory.BRAKES, 120_000))

        assertEquals(items, items.toJson().toLineItems())
    }

    @Test
    fun `unreadable JSON reads as no breakdown rather than making the entry unopenable`() {
        assertTrue("not json at all".toLineItems().isEmpty())
        assertTrue("""{"unexpected":"shape"}""".toLineItems().isEmpty())
    }

    @Test
    fun `a category written by a newer build keeps its line, as OTHER`() {
        val restored = """[{"label":"Sunroof seal","category":"SUNROOF","amount_paise":45000}]""".toLineItems()

        assertEquals(1, restored.size)
        assertEquals(ServiceCategory.OTHER, restored.single().category)
        assertEquals("Sunroof seal", restored.single().label)
        assertEquals(45_000, restored.single().amount.paise)
    }

    @Test
    fun `one impossible line is dropped without taking the rest with it`() {
        val restored = """
            [{"label":"Good","category":"BRAKES","amount_paise":1000},
             {"label":"Negative","category":"BRAKES","amount_paise":-500}]
        """.trimIndent().toLineItems()

        assertEquals(listOf("Good"), restored.map { it.label })
    }
}
