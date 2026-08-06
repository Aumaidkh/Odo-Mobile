package com.hopcape.odo.infrastructure.ai.parsing

import kotlinx.datetime.LocalDate

/**
 * Reads dates out of a line of OCR text.
 *
 * Shared by the bill and document parsers because a date on a workshop bill and a date on
 * an insurance policy are printed the same way, and a fix to one has to be a fix to both.
 * Numeric (`14/07/2026`) and spelled-out (`26 Oct 2023`) forms are both handled, and a line
 * that fails is retried with OCR digit lookalikes repaired — a handwritten `07/04/20`
 * routinely arrives as `O7/O4/2O`.
 *
 * Two-digit years are read as 20xx. Every vehicle paper Odo reads is current, so a `26` is
 * 2026; treating it as 1926 would produce a date nothing could use.
 */
internal object DateReader {

    /** The first date on the line, or null when it holds none. */
    fun first(line: String): LocalDate? = all(line).firstOrNull()

    /**
     * Every date on the line, in the order printed.
     *
     * A document line often carries two — "Valid From 01/01/2024 To 31/12/2024" — and which
     * is which is the caller's decision, not this one's.
     */
    fun all(line: String): List<LocalDate> = positioned(line).map { (_, date) -> date }

    /**
     * Every date on the line with the character it starts at.
     *
     * The position is what lets a caller say "the date *after* the words `valid till`" — a
     * line often carries a start and an end date, and only where each sits says which is which.
     */
    fun positioned(line: String): List<Pair<Int, LocalDate>> {
        val direct = datesIn(line)
        if (direct.isNotEmpty()) return direct
        return repairDigitTokens(line)?.let(::datesIn).orEmpty()
    }

    private fun datesIn(line: String): List<Pair<Int, LocalDate>> {
        val numeric = NUMERIC_DATE.findAll(line).mapNotNull { match ->
            val (day, month, rawYear) = match.destructured
            match.range.first to buildDate(rawYear.toInt().asFullYear(), month.toInt(), day.toInt())
        }
        val monthFirst = MONTH_FIRST_DATE.findAll(line).mapNotNull { match ->
            val (monthName, day, year) = match.destructured
            match.range.first to buildDate(year.toInt(), monthName.toMonth(), day.toInt())
        }
        val dayFirst = DAY_FIRST_DATE.findAll(line).mapNotNull { match ->
            val (day, monthName, year) = match.destructured
            match.range.first to buildDate(year.toInt(), monthName.toMonth(), day.toInt())
        }
        return (numeric + monthFirst + dayFirst)
            .sortedBy { (position, _) -> position }
            .mapNotNull { (position, date) -> date?.let { position to it } }
            .toList()
    }

    private fun Int.asFullYear(): Int = if (this < 100) 2000 + this else this

    private fun String.toMonth(): Int = MONTHS.indexOfFirst { startsWith(it, ignoreCase = true) } + 1

    private fun buildDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate(year, month, day) }.getOrNull()

    /**
     * The line with digit lookalikes swapped back, or null when nothing changed.
     *
     * Only tokens that already carry a date separator *and* a real digit are touched: a
     * token that is all letters is a word, and repairing words would mint dates out of prose.
     */
    private fun repairDigitTokens(line: String): String? {
        var changed = false
        val rebuilt = line.split(WHITESPACE).joinToString(" ") { token ->
            val eligible = token.any { it.isDigit() } && token.any { it in DATE_SEPARATOR_CHARS }
            if (!eligible) return@joinToString token
            val repaired = token.repairedDigits()
            if (repaired != token) changed = true
            repaired
        }
        return if (changed) rebuilt else null
    }

    const val DATE_SEPARATOR_CHARS = "/.-"

    private val WHITESPACE = Regex("""\s+""")

    /** `14/07/2026`, `14-07-26`, `14.07.2026`. */
    val NUMERIC_DATE = Regex("""\b([0-3]?[0-9])[/.\-]([01]?[0-9])[/.\-]((?:20)?[0-9]{2})\b""")

    val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private const val MONTH_PATTERN =
        """(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"""

    /** `October 26, 2023` / `Oct 26 2023`. */
    val MONTH_FIRST_DATE = Regex(
        """\b$MONTH_PATTERN\.?\s+([0-3]?\d)(?:st|nd|rd|th)?,?\s+((?:19|20)\d{2})\b""",
        RegexOption.IGNORE_CASE,
    )

    /** `26 October 2023` / `26th Oct, 2023`. */
    val DAY_FIRST_DATE = Regex(
        """\b([0-3]?\d)(?:st|nd|rd|th)?\s+$MONTH_PATTERN\.?,?\s+((?:19|20)\d{2})\b""",
        RegexOption.IGNORE_CASE,
    )
}
