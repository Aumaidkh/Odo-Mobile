package com.hopcape.odo.web.blog.presentation.admin.editor

import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.TextRun
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The markers the toolbar writes, and the runs they are read back as.
 *
 * This is where Bold and Italic actually live. The buttons wrap a selection in
 * `**` or `*`; if that text does not parse back into the flags it was meant to
 * set, the buttons look like they do nothing — which is exactly what happened
 * while italic was not in the model at all.
 */
class BlockTextTest {

    private fun paragraph(text: String) = ArticleBlock.Paragraph(emptyList()).withText(text)

    private fun runsOf(text: String) = (paragraph(text) as ArticleBlock.Paragraph).runs

    @Test
    fun `bold survives the round trip`() {
        val block = paragraph("plain **bold** plain")
        assertEquals(
            listOf(TextRun("plain "), TextRun("bold", bold = true), TextRun(" plain")),
            (block as ArticleBlock.Paragraph).runs,
        )
        assertEquals("plain **bold** plain", block.editableText())
    }

    @Test
    fun `italic survives the round trip`() {
        val block = paragraph("plain *slanted* plain")
        assertEquals(
            listOf(TextRun("plain "), TextRun("slanted", italic = true), TextRun(" plain")),
            (block as ArticleBlock.Paragraph).runs,
        )
        assertEquals("plain *slanted* plain", block.editableText())
    }

    @Test
    fun `bold is read before italic, or its opening marker is a stray asterisk`() {
        // "**" has to be checked before "*" at every position. The other way round,
        // the first character of a bold marker opens an italic run and the second
        // is left over as text.
        assertEquals(listOf(TextRun("both", bold = true)), runsOf("**both**"))
    }

    @Test
    fun `a run can be both`() {
        assertEquals(listOf(TextRun("loud", bold = true, italic = true)), runsOf("***loud***"))
    }

    @Test
    fun `a lone asterisk stays a lone asterisk`() {
        // Somebody typing "2 * 3", or halfway through opening a pair. Neither
        // should turn the rest of the paragraph slanted.
        assertEquals(listOf(TextRun("2 * 3")), runsOf("2 * 3"))
    }

    @Test
    fun `an underscore is not emphasis`() {
        // snake_case appears in these posts far more often than underscored
        // emphasis does.
        assertEquals(listOf(TextRun("read snake_case_names")), runsOf("read snake_case_names"))
    }

    @Test
    fun `an empty pair round trips to nothing`() {
        // What the toolbar leaves behind when somebody presses B and then types
        // nothing. It must not become a run of empty text.
        assertEquals(emptyList(), runsOf("****"))
    }

    @Test
    fun `an action card is added with its button already written`() {
        // The one block that is worse empty than absent: a call to action whose
        // button says nothing.
        val block = BlockKind.ACTION.empty() as ArticleBlock.AppShowcase
        assertEquals("Download Odo", block.callToAction)
    }
}
