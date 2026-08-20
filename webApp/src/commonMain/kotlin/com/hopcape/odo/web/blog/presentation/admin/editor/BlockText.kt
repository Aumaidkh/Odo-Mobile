package com.hopcape.odo.web.blog.presentation.admin.editor

import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.TextRun

/**
 * How a block is edited as plain text, and read back.
 *
 * The stored model is a list of styled runs; a text field holds a string. Bold is
 * carried across that gap as `**markers**`, which stay visible while writing.
 *
 * That is a real trade-off and worth naming: the design shows bold rendered in the
 * editor, and this shows the markers. Rendering it properly means a text field
 * whose contents carry style — which Compose can do, but only with a custom
 * transformation per block and a selection model to go with it. The markers work
 * today, round-trip exactly, and are what most people writing markdown already
 * expect. Replacing them later changes this file and nothing else.
 */

/** The text a field shows for this block. */
fun ArticleBlock.editableText(): String = when (this) {
    is ArticleBlock.Section -> text
    is ArticleBlock.Paragraph -> runs.toMarkedText()
    is ArticleBlock.Callout -> runs.toMarkedText()
    // Its own fields are edited in the block, not as one string.
    is ArticleBlock.AppShowcase -> heading
}

/** The block that text describes, keeping whatever the block also carries. */
fun ArticleBlock.withText(text: String): ArticleBlock = when (this) {
    is ArticleBlock.Section -> copy(text = text)
    is ArticleBlock.Paragraph -> copy(runs = text.toRuns())
    is ArticleBlock.Callout -> copy(runs = text.toRuns())
    is ArticleBlock.AppShowcase -> copy(heading = text)
}

/** What the toolbar can add. Not every block type — showcases are placed, not typed. */
enum class BlockKind { PARAGRAPH, HEADING, CALLOUT }

fun BlockKind.empty(): ArticleBlock = when (this) {
    BlockKind.PARAGRAPH -> ArticleBlock.Paragraph(emptyList())
    // The id is generated once, here, and never rewritten from the text. A
    // contents link that changed every time somebody fixed a typo in a heading
    // would break every URL anybody had shared to that section.
    BlockKind.HEADING -> ArticleBlock.Section(id = "section", text = "")
    BlockKind.CALLOUT -> ArticleBlock.Callout(label = "DHYAN DEIN", runs = emptyList())
}

/** Runs to text, with `**` around the bold ones. */
private fun List<TextRun>.toMarkedText(): String = joinToString("") { run ->
    if (run.bold) "**${run.text}**" else run.text
}

/**
 * Text to runs.
 *
 * A pair of `**` opens and closes; an odd one left over is literal, because
 * somebody halfway through typing a bold word should not see the rest of their
 * paragraph turn bold.
 */
private fun String.toRuns(): List<TextRun> {
    if ("**" !in this) return if (isEmpty()) emptyList() else listOf(TextRun(this))
    val runs = mutableListOf<TextRun>()
    var index = 0
    var bold = false
    while (index < length) {
        val marker = indexOf("**", index)
        if (marker < 0) {
            runs += TextRun(substring(index), bold)
            break
        }
        val closing = if (bold) marker else indexOf("**", marker + 2)
        if (closing < 0) {
            // Unmatched: everything from here on is plain, markers and all.
            runs += TextRun(substring(index), bold)
            break
        }
        if (marker > index) runs += TextRun(substring(index, marker), bold)
        index = marker + 2
        bold = !bold
    }
    return runs.filter { it.text.isNotEmpty() }
}
