package com.hopcape.odo.infrastructure.ai.parsing

/** One text run the OCR engine read, with where it sat on the photo. */
internal data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val height: Int get() = bottom - top
}

/**
 * Rebuilds a bill's visual rows from the scattered runs OCR answers with.
 *
 * This is the piece that makes tabular bills parse at all. An engine like ML Kit groups
 * text by *column* — a cash-memo table comes back as "ENGINE OIL" in one block and
 * "1000/-" in another — so the amount-at-line-end rule finds labels with no money and
 * money with no labels. Runs that overlap vertically are one printed row; joining them
 * left-to-right restores "ENGINE OIL  1  1000/-  1000/-", which is the shape the parser
 * was written for.
 *
 * Two runs share a row when their vertical overlap covers at least half the shorter one.
 * Half, not any, because handwriting drifts above and below the ruled line; requiring
 * full containment would split the very rows this exists to join.
 */
internal class OcrRowComposer {

    fun rows(lines: List<OcrLine>): List<String> {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return emptyList()

        val sorted = usable.sortedBy { it.top }
        val bands = mutableListOf<Band>()
        var band = Band(sorted.first())

        for (line in sorted.drop(1)) {
            if (band.sharesRowWith(line)) band.add(line) else {
                bands += band
                band = Band(line)
            }
        }
        bands += band

        return bands.map { it.joined() }
    }

    /** A row being assembled: its members and the vertical span they cover so far. */
    private class Band(first: OcrLine) {
        private val members = mutableListOf(first)
        private var top = first.top
        private var bottom = first.bottom

        fun sharesRowWith(line: OcrLine): Boolean {
            val overlap = minOf(bottom, line.bottom) - maxOf(top, line.top)
            val shorter = minOf(bottom - top, line.height).coerceAtLeast(1)
            return overlap * 2 >= shorter
        }

        fun add(line: OcrLine) {
            members += line
            top = minOf(top, line.top)
            bottom = maxOf(bottom, line.bottom)
        }

        fun joined(): String = members.sortedBy { it.left }.joinToString(" ") { it.text.trim() }
    }
}
