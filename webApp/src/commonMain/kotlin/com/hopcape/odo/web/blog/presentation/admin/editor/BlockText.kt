package com.hopcape.odo.web.blog.presentation.admin.editor

import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.TextRun

/**
 * How a block is edited as plain text, and read back.
 *
 * The stored model is a list of styled runs; a text field holds a string.
 * Emphasis crosses that gap as markers — `**bold**` and `*italic*` — which stay
 * visible while writing.
 *
 * That is a real trade-off and worth naming: the published article shows bold
 * rendered, and the editor shows the markers. Rendering it live means a text
 * field whose contents carry style, which Compose can do but only with a custom
 * transformation and a selection model per block. The markers work today, round
 * trip exactly, and are what anybody who has written markdown already expects.
 *
 * `_` stays literal. Most markdown treats it as emphasis, and `snake_case_names`
 * appear in these posts far more often than underscored emphasis does.
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

/** What the toolbar can add. Not every block type — an image is placed, not typed. */
enum class BlockKind { PARAGRAPH, HEADING, CALLOUT, ACTION }

fun BlockKind.empty(): ArticleBlock = when (this) {
    BlockKind.PARAGRAPH -> ArticleBlock.Paragraph(emptyList())
    // The id is generated once, here, and never rewritten from the text. A
    // contents link that changed every time somebody fixed a typo in a heading
    // would break every URL anybody had shared to that section.
    BlockKind.HEADING -> ArticleBlock.Section(id = "section", text = "")
    BlockKind.CALLOUT -> ArticleBlock.Callout(label = "WORTH KNOWING", runs = emptyList())
    // Placed with its call to action already written. An action card whose button
    // says nothing is the one block that is worse empty than absent.
    BlockKind.ACTION -> ArticleBlock.AppShowcase(
        heading = "",
        body = "",
        callToAction = "Download Odo",
    )
}

/** The markers, in the order they have to be applied to nest correctly. */
const val BOLD_MARKER: String = "**"
const val ITALIC_MARKER: String = "*"

// ── Text to runs and back ────────────────────────────────────────────────────

private fun List<TextRun>.toMarkedText(): String = joinToString("") { run ->
    val inner = if (run.italic) "$ITALIC_MARKER${run.text}$ITALIC_MARKER" else run.text
    if (run.bold) "$BOLD_MARKER$inner$BOLD_MARKER" else inner
}

/**
 * Text to runs.
 *
 * One pass, flags carried along. `**` is checked before `*` at every position, or
 * the opening of a bold run would be read as an italic one followed by a stray
 * asterisk. An unmatched marker at the end stays literal, because somebody
 * halfway through typing a bold word should not watch the rest of the paragraph
 * change weight.
 */
private fun String.toRuns(): List<TextRun> {
    if (BOLD_MARKER !in this && ITALIC_MARKER !in this) {
        return if (isEmpty()) emptyList() else listOf(TextRun(this))
    }

    val runs = mutableListOf<TextRun>()
    val buffer = StringBuilder()
    var bold = false
    var italic = false
    var index = 0

    fun flush() {
        if (buffer.isNotEmpty()) {
            runs += TextRun(buffer.toString(), bold = bold, italic = italic)
            buffer.clear()
        }
    }

    while (index < length) {
        when {
            startsWith(BOLD_MARKER, index) && closes(BOLD_MARKER, index, bold) -> {
                flush()
                bold = !bold
                index += BOLD_MARKER.length
            }

            this[index] == '*' && closes(ITALIC_MARKER, index, italic) -> {
                flush()
                italic = !italic
                index += ITALIC_MARKER.length
            }

            else -> {
                buffer.append(this[index])
                index++
            }
        }
    }
    flush()
    return runs.filter { it.text.isNotEmpty() }
}

/**
 * Whether a marker at [index] is one half of a pair.
 *
 * A marker that closes an open run always counts. One that would open a run only
 * counts if its partner exists further along — otherwise it is a lone asterisk
 * somebody typed, and it should stay one.
 */
private fun String.closes(marker: String, index: Int, open: Boolean): Boolean =
    open || indexOf(marker, startIndex = index + marker.length) >= 0
