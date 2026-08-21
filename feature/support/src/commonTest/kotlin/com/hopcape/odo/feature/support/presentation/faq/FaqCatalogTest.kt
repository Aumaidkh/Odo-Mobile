package com.hopcape.odo.feature.support.presentation.faq

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaqCatalogTest {

    private val faqs = listOf(
        ResolvedFaq("offline", "Does Odo work without internet?", "Everything is saved on the phone first."),
        ResolvedFaq("background", "Why does Odo ask for location?", "Only to measure a drive in the background."),
        ResolvedFaq("pro", "What does Pro add?", "Unlimited bill scans and a printable PDF."),
    )

    @Test
    fun `a blank query matches nothing`() {
        assertTrue(faqs.matching("").isEmpty())
        assertTrue(faqs.matching("   ").isEmpty())
    }

    @Test
    fun `matches a word in the question`() {
        assertEquals(listOf("pro"), faqs.matching("Pro add").map { it.id })
    }

    @Test
    fun `matches a word that only appears in the answer`() {
        // The whole point of searching answers: "background" is nowhere in that question.
        assertEquals(listOf("background"), faqs.matching("background").map { it.id })
    }

    @Test
    fun `ignores case`() {
        assertEquals(faqs.matching("INTERNET"), faqs.matching("internet"))
        assertEquals(listOf("offline"), faqs.matching("INTERNET").map { it.id })
    }

    @Test
    fun `matches a partial word`() {
        assertEquals(listOf("pro"), faqs.matching("unlimit").map { it.id })
    }

    @Test
    fun `surrounding spaces do not change the result`() {
        assertEquals(listOf("offline"), faqs.matching("  internet  ").map { it.id })
    }

    @Test
    fun `an unmatched query returns nothing`() {
        assertTrue(faqs.matching("carburettor").isEmpty())
    }

    @Test
    fun `every shipped entry has a distinct id`() {
        // The list and the search results both key on it; a duplicate would crash LazyColumn.
        val ids = FAQ_ENTRIES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
