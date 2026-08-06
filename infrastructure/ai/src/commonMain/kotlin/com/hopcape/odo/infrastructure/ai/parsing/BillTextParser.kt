package com.hopcape.odo.infrastructure.ai.parsing

import com.hopcape.odo.core.domain.scan.model.BillType
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ExtractedLineItem
import com.hopcape.odo.core.domain.scan.model.ExtractionConfidence
import com.hopcape.odo.core.domain.scan.model.ScanId
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/**
 * Turns the raw text an OCR engine read off a bill into an [ExtractedBill].
 *
 * This is the interim brain behind the on-device scanner: ML Kit answers with lines of
 * text and nothing else, so what a line *means* — an amount, a date, the total — is
 * decided here, by pattern. Pure Kotlin on purpose: every rule in this file is exercised
 * by unit tests, which is where the correctness of the ML Kit path actually lives.
 *
 * Honesty over reach, per the PRD: heuristics against thermal-printer text cannot tell a
 * printed bill from a handwritten one, so every result is [BillType.UNKNOWN] — which the
 * domain already treats as "the owner checks every field". The confidence is a parse-count
 * heuristic capped at [CONFIDENCE_CAP], below the trustworthy threshold, so an on-device
 * read never presents itself with the certainty reserved for the model-backed path.
 */
internal class BillTextParser {

    fun parse(scanId: ScanId, lines: List<String>): ExtractedBill {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }

        val serviceDate = cleaned.firstNotNullOfOrNull(::dateIn)
        val odometerKm = cleaned.firstNotNullOfOrNull(::odometerIn)
        val workshopName = workshopIn(cleaned)

        val pricedLines = cleaned.mapNotNull { line -> priced(line)?.let { line to it } }
        val totalPaise = pricedLines.lastOrNull { (line, _) -> isTotalLine(line) }?.second?.paise
        val lineItems = pricedLines
            .filterNot { (line, _) -> isTotalLine(line) }
            .mapNotNull { (line, read) -> lineItem(line, read) }

        return ExtractedBill(
            scanId = scanId,
            billType = BillType.UNKNOWN,
            confidence = confidence(
                hasItems = lineItems.isNotEmpty(),
                hasTotal = totalPaise != null,
                hasDate = serviceDate != null,
                hasOdometer = odometerKm != null,
                hasWorkshop = workshopName != null,
            ),
            serviceDate = serviceDate,
            odometerKm = odometerKm,
            workshopName = workshopName,
            lineItems = lineItems,
            total = totalPaise?.let { paise -> Amount.of(paise).getOrNull() },
        )
    }

    /** A money reading off a line's end: how much, and whether OCR repair was needed. */
    private data class PricedRead(val paise: Long, val fuzzy: Boolean, val labelPart: String)

    /**
     * The amount a line ends with, or null when the line prices nothing.
     *
     * Anchored to the end because bills right-align their money column — on a composed
     * table row that also skips the rate column and lands on the amount. The lookbehind
     * refuses a match inside a longer digit run, so a phone number or a GSTIN never reads
     * as money. A bare integer with no currency mark, separator or decimals is accepted
     * only within [BARE_AMOUNT_MIN]..[BARE_AMOUNT_MAX].
     *
     * When the raw text does not match, one repair is tried: OCR reads handwritten money
     * with letter lookalikes — `200/-` arrives as `Q0o/-` — so the trailing token has
     * those swapped for the digits they resemble and the match runs once more. Only a
     * token that already carries a real digit is eligible, because "normalising" a plain
     * word like `OIL` would mint money out of letters. A repaired read is [PricedRead.fuzzy]
     * and surfaces as `needsCheck` on the item: recovered, but the owner's eyes go first.
     */
    private fun priced(line: String): PricedRead? {
        // OCR tacks stray marks onto line ends — a dot from the dotted leader, a pipe from
        // a table rule. They sit between the money and the anchor, so they go first.
        val effective = line.trimEnd(*TRAILING_JUNK)

        AMOUNT.find(effective)?.let { match ->
            paiseOf(match)?.let { return PricedRead(it, fuzzy = false, labelPart = effective.removeRange(match.range)) }
        }

        val tokens = effective.trim().split(WHITESPACE)
        val last = tokens.lastOrNull() ?: return null
        if (last.none { it.isDigit() }) return null
        val repaired = last.repairedDigits()
        if (repaired == last) return null
        // A repaired read must still *look* like money — a `/-` suffix, a currency mark,
        // grouping or decimals. Without that bar, the tail of a GSTIN (`…1Z0` → 120)
        // mints an amount out of an id, which is worse than reading nothing.
        if (MONEY_MARKS.none { repaired.contains(it) }) return null

        val rebuilt = (tokens.dropLast(1) + repaired).joinToString(" ")
        val match = AMOUNT.find(rebuilt) ?: return null
        val paise = paiseOf(match) ?: return null
        return PricedRead(paise, fuzzy = true, labelPart = rebuilt.removeRange(match.range))
    }

    private fun paiseOf(match: MatchResult): Long? {
        val currency = match.groupValues[1]
        val digits = match.groupValues[2]
        val decimals = match.groupValues[3]
        val marked = currency.isNotEmpty() || digits.contains(',') || decimals.isNotEmpty()

        val rupees = digits.replace(",", "").toLongOrNull() ?: return null
        if (!marked && rupees !in BARE_AMOUNT_MIN..BARE_AMOUNT_MAX) return null

        val fraction = when (decimals.length) {
            2 -> decimals.toLong()
            1 -> decimals.toLong() * 10
            else -> 0L
        }
        return rupees * 100 + fraction
    }

    /** The first thing on the bill that parses as a date — see [DateReader] for the forms. */
    private fun dateIn(line: String): LocalDate? = DateReader.first(line)

    /**
     * A reading from a line that mentions the odometer. Bounded to a plausible range so a
     * bill number on a line like "Job Card KM-9876543" does not become a reading. The
     * lookalike repair gets a second try here too — `43,2lO` is a reading with two OCR
     * slips in it, not a mystery string.
     */
    private fun odometerIn(line: String): Int? {
        if (!ODOMETER_HINT.containsMatchIn(line)) return null
        // The repaired reading goes first: on `43,2lO` the raw text still yields a
        // plausible-looking partial (432), and a wrong reading beats no reading only
        // when it is actually right.
        return repairDigitTokens(line, requireDateSeparator = false)?.let(::odometerValue)
            ?: odometerValue(line)
    }

    /**
     * The reading nearest *after* the hint word, falling back to anywhere on the line.
     * Position matters on a composed row: "Vehicle: MH-12-AB-5678 Odometer: 45,000" holds
     * two plausible numbers, and the one following "Odometer" is the reading.
     */
    private fun odometerValue(line: String): Int? {
        val hintEnd = ODOMETER_HINT.find(line)?.range?.last ?: -1
        val readings = ODOMETER_VALUE.findAll(line)
            .mapNotNull { match ->
                match.value.replace(",", "").toIntOrNull()
                    ?.takeIf { it in ODOMETER_MIN..ODOMETER_MAX }
                    ?.let { match.range.first to it }
            }
            .toList()
        return (readings.firstOrNull { (start, _) -> start > hintEnd } ?: readings.firstOrNull())?.second
    }

    /**
     * The line with digit lookalikes swapped back, or null when nothing changed. Only
     * tokens already carrying a real digit are touched (and, for dates, a separator too):
     * a token that is all letters is a word, and words stay words.
     */
    private fun repairDigitTokens(line: String, requireDateSeparator: Boolean): String? {
        var changed = false
        val rebuilt = line.split(WHITESPACE).joinToString(" ") { token ->
            val eligible = token.any { it.isDigit() } &&
                (!requireDateSeparator || token.any { it in DATE_SEPARATOR_CHARS })
            if (!eligible) {
                token
            } else {
                val repaired = token.repairedDigits()
                if (repaired != token) changed = true
                repaired
            }
        }
        return if (changed) rebuilt else null
    }

    /**
     * The workshop is the name printed above the bill's own furniture.
     *
     * Anchored on a marker row — "CASH MEMO / BILL", "TAX INVOICE", the DATE line — and
     * searched upward from there, nearest first. Anchoring matters because the photo's top
     * rows are frequently not the bill at all: a scan taken over a desk or off a screen
     * carries stray text above the paper, and "the first plausible line on the photo" picks
     * that up. The marker is the first thing that is unmistakably the bill, so the name is
     * whatever sits just above it. With no marker in sight, the old top-of-photo guess
     * stands. A candidate reads as a name when it has letters, no digits, and none of the
     * furniture words.
     */
    private fun workshopIn(lines: List<String>): String? {
        val marker = lines.indexOfFirst { BILL_MARKER.containsMatchIn(it) }
        val candidates = if (marker > 0) {
            lines.subList((marker - HEADER_LINES).coerceAtLeast(0), marker).asReversed()
        } else {
            lines.take(HEADER_LINES)
        }
        return candidates.firstOrNull { line ->
            line.length in WORKSHOP_LENGTH &&
                line.any { it.isLetter() } &&
                line.none { it.isDigit() } &&
                HEADER_NOISE.none { line.contains(it, ignoreCase = true) }
        }
    }

    private fun isTotalLine(line: String): Boolean = TOTAL_HINT.containsMatchIn(line)

    /** A priced line becomes an item when stripping the money leaves a real label. */
    private fun lineItem(line: String, read: PricedRead): ExtractedLineItem? {
        // A line that carries the odometer reading prices nothing — "KM Reading: 43,210"
        // is a reading, not a ₹43,210 charge.
        if (odometerIn(line) != null) return null
        // The bill's own paperwork — GSTIN, invoice number, customer, phone, tax rows —
        // is full of numbers that pass for money and never is any. A GSTIN's tail or a
        // phone number as a line item is exactly the kind of confident nonsense that
        // destroys trust in the whole extraction.
        if (ITEM_NOISE.containsMatchIn(line)) return null
        val label = labelOf(read.labelPart)
        if (label.length < MIN_LABEL_LENGTH || label.none { it.isLetter() }) return null
        if (dateIn(line) != null && label.replace(DateReader.NUMERIC_DATE, "").none { it.isLetter() }) return null
        val amount = Amount.of(read.paise).getOrNull() ?: return null
        return ExtractedLineItem(label = label, amount = amount, needsCheck = read.fuzzy)
    }

    /**
     * The amount-stripped line with everything that is not the item's name removed.
     *
     * A composed table row reads "3. FRONT BRAKE PADS 1 2500/- 2500/-": the serial number
     * leads, and between the name and the amount sit the qty and rate columns. The
     * remaining trailing tokens are dropped while they are column debris — a number, a
     * rate, a dash where a rate had none — and never a token with letters in it, so
     * "5W40" stays part of an oil's name.
     */
    private fun labelOf(labelPart: String): String {
        val tokens = labelPart
            .replace(SERIAL_PREFIX, "")
            .trim()
            .split(WHITESPACE)
            .toMutableList()
        while (tokens.isNotEmpty() && tokens.last().isColumnDebris()) {
            tokens.removeAt(tokens.size - 1)
        }
        // An item's name is a few words; anything longer is OCR sweeping neighbouring
        // text into the row. The first words are the name — bills lead with it.
        return tokens.take(MAX_LABEL_WORDS).joinToString(" ").trim(*LABEL_TRIM).trim()
    }

    /**
     * Whether a trailing token is column leftovers rather than part of the name. Checked
     * as written and again with digit lookalikes repaired, so a rate OCR mangled into
     * `Q50o/-` is dropped like the `2500/-` it is — while `5W40`, whose letters resemble
     * no digits, stays.
     */
    private fun String.isColumnDebris(): Boolean =
        COLUMN_DEBRIS.matches(this) ||
            (any { it.isDigit() } && COLUMN_DEBRIS.matches(repairedDigits()))

    /**
     * How much of the bill the patterns accounted for, as the 0..100 self-estimate the
     * domain expects — capped so this path never crosses the "trustworthy" threshold.
     */
    private fun confidence(
        hasItems: Boolean,
        hasTotal: Boolean,
        hasDate: Boolean,
        hasOdometer: Boolean,
        hasWorkshop: Boolean,
    ): ExtractionConfidence {
        var score = 0
        if (hasItems) score += ITEMS_POINTS
        if (hasTotal) score += TOTAL_POINTS
        if (hasDate) score += DATE_POINTS
        if (hasOdometer) score += ODOMETER_POINTS
        if (hasWorkshop) score += WORKSHOP_POINTS
        return ExtractionConfidence.of(minOf(score, CONFIDENCE_CAP)).getOrNull()
            ?: ExtractionConfidence.NONE
    }

    private companion object {
        /**
         * `₹ 1,234.56`, `Rs.350`, `500/-`, `500-`, or a bare `400` — anchored to the
         * line's end. Two ways in: an explicit currency mark, or no mark but also no
         * digit/date-separator immediately before — which is what keeps `14/07/2026`'s
         * year and a dotted date's tail from reading as money. The `/-` suffix may lose
         * its slash to OCR, so a bare trailing dash is accepted too.
         */
        val AMOUNT = Regex(
            """(?:(₹|rs\.?|inr)\s*|(?<![0-9,./\-]))([0-9]{1,3}(?:,[0-9]{2,3})*|[0-9]+)(?:\.([0-9]{1,2}))?\s*(?:/-|[-–—])?\s*$""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * What OCR makes of handwritten digits, mapped back. Applied only to a line's
         * trailing token, and only when it already holds a real digit — seen in the field
         * as `200/-` → `Q0o/-` and `2500/-` → `Q50o/-`.
         */
        /** Stray marks OCR appends to a line — a leader dot, a table rule — never money. */
        val TRAILING_JUNK = charArrayOf(' ', '.', '|', '*', '·', '~', '`', '\'', '"')

        /** What separates the parts of a written date. */
        val DATE_SEPARATOR_CHARS = DateReader.DATE_SEPARATOR_CHARS

        /** What a repaired amount must carry to count as money at all. */
        val MONEY_MARKS = listOf("/-", "₹", ",", ".")

        /**
         * Lines that are the bill's paperwork, never a service rendered. Anything matching
         * is barred from becoming a line item, whatever number it happens to end with.
         *
         * Two tiers on purpose. The first alternation is words that mean paperwork wherever
         * they appear (a GSTIN, a tax row, "unit price"). The second is words that are only
         * paperwork as a *labelled field* — "Vehicle:" is the bill's header, while "General
         * Vehicle Inspection" is a service the owner paid for — so those need the colon.
         */
        val ITEM_NOISE = Regex(
            """\b(?:gstin|gst\s*no|[cs]gst|igst|subtotal|sub\s*total|invoice|bill\s*no|receipt\s*no|unit\s*price|signature)\b""" +
                """|\b(?:mobile|phone|contact|customer|vehicle|veh\s*no|reg\s*no|description|date|odometer|mechanic|manager|s\.?\s*no)\s*[.:#]""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * The words that mean "this row is the bill's own furniture". One vocabulary,
         * two uses: [BILL_MARKER] anchors the workshop search on the first furniture row,
         * and [HEADER_NOISE] rejects furniture rows as workshop-name candidates — a word
         * added to one and not the other would skew the two against each other.
         */
        val FURNITURE_WORDS = listOf("invoice", "bill", "receipt", "estimate", "cash memo")

        /** A row that is unmistakably the bill's own furniture — the workshop-search anchor. */
        val BILL_MARKER = Regex(
            FURNITURE_WORDS.joinToString(
                separator = "|",
                prefix = """\b(?:""",
                postfix = """|date\s*:|s\.?\s*no)\b""",
            ) { it.replace(" ", """\s*""") },
            RegexOption.IGNORE_CASE,
        )

        /**
         * Bare integers read as money only in this range. The ceiling is five digits on
         * purpose: a six-digit number with no currency mark, grouping or decimals is a
         * pin code or an id far more often than a ₹1,00,000+ service — which real bills
         * print with marks.
         */
        const val BARE_AMOUNT_MIN = 10L
        const val BARE_AMOUNT_MAX = 99_999L

        val ODOMETER_HINT = Regex("""\b(?:odo(?:meter)?|kms?|km\s*reading|reading)\b""", RegexOption.IGNORE_CASE)
        val ODOMETER_VALUE = Regex("""[0-9][0-9,]{2,7}""")
        const val ODOMETER_MIN = 100
        const val ODOMETER_MAX = 500_000

        val TOTAL_HINT = Regex(
            """\b(?:total|grand\s*total|net\s*amount|amount\s*(?:payable|due)|payable)\b""",
            RegexOption.IGNORE_CASE,
        )

        const val HEADER_LINES = 4
        /** Long enough for "... AUTO GARAGE & SERVICE CENTER" headers, short enough to skip prose. */
        val WORKSHOP_LENGTH = 3..56
        val HEADER_NOISE = FURNITURE_WORDS + "gst"

        /** `1.` / `12)` / a bare `1` at the start of a composed table row — the S.NO. column. */
        val SERIAL_PREFIX = Regex("""^\s*[0-9]{1,2}[.)]?\s+""")

        /** Trailing qty/rate-column leftovers: `1`, `1.`, `x2`, `1,000/-`, `500-`, `—`. */
        val COLUMN_DEBRIS = Regex(
            """(?:₹|rs\.?|inr)?[0-9][0-9,]*(?:\.[0-9]{1,2})?[.)]?(?:/-|[-–—])?|[xX][0-9]{1,3}|[—–-]+""",
            RegexOption.IGNORE_CASE,
        )

        val WHITESPACE = Regex("""\s+""")

        val LABEL_TRIM = charArrayOf('-', '—', '–', ':', '.', '…', '*', '_', ' ')
        const val MIN_LABEL_LENGTH = 3
        const val MAX_LABEL_WORDS = 4

        /* Confidence weights — parse coverage, not accuracy. */
        const val ITEMS_POINTS = 30
        const val TOTAL_POINTS = 25
        const val DATE_POINTS = 15
        const val ODOMETER_POINTS = 15
        const val WORKSHOP_POINTS = 5

        /** Below [ExtractionConfidence.HIGH] by design — a heuristic read is never "trustworthy". */
        const val CONFIDENCE_CAP = 80
    }
}

/**
 * What OCR makes of handwritten digits, mapped back — seen in the field as `200/-` → `Q0o/-`.
 *
 * Top-level rather than inside the parser because the whole ML Kit pipeline shares this
 * vocabulary: the parser repairs with it, and the extractor's strip pass uses the key set
 * to recognise a mangled amount as money-shaped. One map keeps the two in step.
 */
internal val DIGIT_LOOKALIKES = mapOf(
    'O' to '0', 'o' to '0',
    'l' to '1', 'I' to '1',
    'S' to '5', 's' to '5',
    'B' to '8',
    'Q' to '2', 'q' to '2',
    'Z' to '2', 'z' to '2',
    'G' to '6',
)

/** This string with each digit lookalike swapped for the digit it resembles. */
internal fun String.repairedDigits(): String =
    map { DIGIT_LOOKALIKES[it] ?: it }.joinToString("")
