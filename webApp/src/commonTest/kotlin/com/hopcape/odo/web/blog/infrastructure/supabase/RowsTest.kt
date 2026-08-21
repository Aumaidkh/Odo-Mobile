package com.hopcape.odo.web.blog.infrastructure.supabase

import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.TextRun
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The article body, across the boundary.
 *
 * This is the one shape that goes into the database as JSON and comes back out
 * expected to be identical. Everything else on a post is a column Postgres knows
 * the type of; the body is bytes we wrote and have to be able to read.
 */
class RowsTest {

    private val json = Json

    @Test
    fun `every block type survives the round trip`() {
        val original = listOf(
            ArticleBlock.Paragraph(
                listOf(
                    TextRun("There are two kinds. "),
                    TextRun("On the spot", bold = true),
                    TextRun(" — the police stopped you."),
                ),
            ),
            ArticleBlock.Section("what-a-challan-is", "What a challan is"),
            ArticleBlock.Callout("WORTH KNOWING", listOf(TextRun("Ninety days and it goes to court."))),
            ArticleBlock.AppShowcase("Odo has a screen for this", "Enter the number.", "Download Odo", "/x.png", "https://odoapp.in/costs"),
        )

        val decoded = decodeBlocks(json.parseToJsonElement(encodeBlocks(original)))

        assertEquals(original, decoded, "a body written by the editor has to read back as itself")
    }

    @Test
    fun `bold survives, because it is the only emphasis there is`() {
        val original = listOf(ArticleBlock.Paragraph(listOf(TextRun("plain "), TextRun("bold", bold = true))))
        val decoded = decodeBlocks(json.parseToJsonElement(encodeBlocks(original)))
        assertEquals(listOf(false, true), (decoded.first() as ArticleBlock.Paragraph).runs.map { it.bold })
    }

    @Test
    fun `a block type this version does not know is dropped, not fatal`() {
        // A body written by a newer CMS. Losing one block beats losing the article.
        val body = """[{"type":"paragraph","runs":[{"text":"kept","bold":false}]},{"type":"video","src":"x"}]"""
        val decoded = decodeBlocks(json.parseToJsonElement(body))
        assertEquals(1, decoded.size)
        assertTrue(decoded.first() is ArticleBlock.Paragraph)
    }

    @Test
    fun `a body that is not an array reads as empty rather than throwing`() {
        assertEquals(emptyList(), decodeBlocks(json.parseToJsonElement("""{"oops":true}""")))
    }

    @Test
    fun `a heading with no stored id gets one from its text`() {
        // Rows written before ids were stored, and rows written by hand in SQL.
        val decoded = decodeBlocks(json.parseToJsonElement("""[{"type":"section","text":"How long you have"}]"""))
        assertEquals(ArticleBlock.Section("how-long-you-have", "How long you have"), decoded.first())
    }

    @Test
    fun `a search term cannot break out of a PostgREST filter`() {
        // `&` would end the filter and start a new query parameter; `%` and quotes
        // are the rest of what a reader can type into the search box.
        val encoded = "tyre & warranty 100%".encoded()
        assertTrue('&' !in encoded, "an ampersand has to be escaped: $encoded")
        assertTrue('%' !in encoded.removePrefix("%").replace(Regex("%[0-9A-F]{2}"), ""), encoded)
    }

    @Test
    fun `a value going into a hand-built payload is escaped`() {
        assertEquals("""he said \"no\"""", """he said "no"""".jsonEscaped())
        assertEquals("""a\\b""", """a\b""".jsonEscaped())
    }
}
