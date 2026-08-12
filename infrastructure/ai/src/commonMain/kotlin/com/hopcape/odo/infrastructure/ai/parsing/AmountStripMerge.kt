package com.hopcape.odo.infrastructure.ai.parsing

/**
 * The rules of the two-pass scheme: which of the full pass's runs the strip pass may
 * replace, and when the strip pass is worth running at all.
 *
 * Pure and in the parsing package on purpose — this substitution is the most bug-prone
 * step of the on-device pipeline, and this package is where the ML Kit path's correctness
 * lives under test. The extractor supplies coordinates; the rules live here.
 */

/**
 * The full pass's runs with strip-pass money substituted in. A full-pass money run in the
 * strip region is dropped only when a strip run actually covers its row — substitution,
 * never deletion.
 *
 * Row matching here is *any* vertical overlap, deliberately looser than the composer's
 * half-of-the-shorter-run rule: a replacement should err toward finding the row it
 * belongs to, while composition errs toward keeping distinct rows apart.
 */
internal fun mergeAmountStrip(
    fullRuns: List<OcrLine>,
    stripRuns: List<OcrLine>,
    stripLeft: Int,
): List<OcrLine> {
    if (stripRuns.isEmpty()) return fullRuns
    val kept = fullRuns.filterNot { run ->
        val centreInStrip = (run.left + run.right) / 2 >= stripLeft
        centreInStrip && run.text.isMoneyShaped() && stripRuns.any { it.overlapsVertically(run) }
    }
    return kept + stripRuns
}

/**
 * Whether the full pass already read the money column cleanly — every money-shaped run in
 * the strip region is plain digits and marks, with no lookalike letters to repair. When it
 * did, the second pass has nothing to improve and its latency is pure cost.
 */
internal fun stripPassRedundant(fullRuns: List<OcrLine>, stripLeft: Int): Boolean {
    val moneyInStrip = fullRuns.filter { run ->
        (run.left + run.right) / 2 >= stripLeft && run.text.isMoneyShaped()
    }
    return moneyInStrip.isNotEmpty() && moneyInStrip.none { run -> run.text.any { it.isLetter() } }
}

/** Mostly digits, digit lookalikes and money punctuation — a rate or an amount. */
internal fun String.isMoneyShaped(): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_MONEY_TOKEN_LENGTH) return false
    if (trimmed.none { it.isDigit() }) return false
    val foreignLetters = trimmed.count { it.isLetter() && it !in DIGIT_LOOKALIKES }
    return foreignLetters <= trimmed.length / 3
}

private fun OcrLine.overlapsVertically(other: OcrLine): Boolean =
    minOf(bottom, other.bottom) - maxOf(top, other.top) > 0

private const val MAX_MONEY_TOKEN_LENGTH = 14
