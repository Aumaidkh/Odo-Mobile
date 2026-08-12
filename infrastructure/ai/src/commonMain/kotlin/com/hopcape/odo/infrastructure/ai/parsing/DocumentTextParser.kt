package com.hopcape.odo.infrastructure.ai.parsing

import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.core.domain.scan.model.ExtractionConfidence
import com.hopcape.odo.core.domain.scan.model.ScanId
import kotlinx.datetime.LocalDate

/**
 * Turns the text an OCR engine read off a vehicle paper into an [ExtractedDocument].
 *
 * The document counterpart of [BillTextParser], and the same kind of thing: ML Kit answers
 * with lines of text, and what a line *means* — the expiry, the kind of paper, the insurer's
 * name — is decided here by pattern. Pure Kotlin, so every rule is unit-tested.
 *
 * The expiry date is what this exists for. A vault entry with no expiry produces no
 * reminder, which is most of what the vault is for, so the date hints are the part worth
 * being careful about: a paper carries several dates and only one of them is the day cover
 * runs out.
 *
 * Confidence is parse coverage, not accuracy, and is capped at [CONFIDENCE_CAP] — below
 * [ExtractionConfidence.HIGH] — so an on-device read never presents itself as trustworthy.
 * The owner confirms every field on the review screen regardless.
 */
internal class DocumentTextParser {

    fun parse(scanId: ScanId, lines: List<String>): ExtractedDocument {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }

        val type = typeIn(cleaned)
        val dates = datesIn(cleaned)
        val title = titleIn(cleaned, type)

        return ExtractedDocument(
            scanId = scanId,
            confidence = confidence(
                hasType = type != null,
                hasExpiry = dates.expiresOn != null,
                hasIssued = dates.issuedOn != null,
                hasTitle = title != null,
            ),
            documentType = type,
            suggestedTitle = title,
            issuedOn = dates.issuedOn,
            expiresOn = dates.expiresOn,
        )
    }

    /* ------------------------------ What kind of paper ------------------------------ */

    /**
     * The paper's type, by how many of each kind's words appear on it.
     *
     * Counted across the whole document rather than matched on the first hit, because these
     * papers quote each other: an RC names the insurance policy, and a PUC certificate names
     * the registration number. The kind with the most of its own vocabulary wins, and a tie
     * answers null — the owner picks rather than being told something half-decided.
     */
    private fun typeIn(lines: List<String>): DocumentType? {
        val text = lines.joinToString(" ")
        val scores = TYPE_WORDS.mapValues { (_, words) -> words.count { it.containsMatchIn(text) } }
        val best = scores.maxByOrNull { it.value } ?: return null
        if (best.value == 0) return null
        val tied = scores.count { it.value == best.value } > 1
        return if (tied) null else best.key
    }

    /* ------------------------------ The dates ------------------------------ */

    private data class Dates(val issuedOn: LocalDate?, val expiresOn: LocalDate?)

    /**
     * The issue and expiry dates.
     *
     * A labelled date wins outright: "Valid Till 14/03/2027" says what it is. The label's
     * own date is the first one printed *after* it, because a line commonly carries both
     * ends — "Valid From 15/03/2026 To 14/03/2027" — and only position separates them.
     *
     * A "from" label with two dates behind it is a cover period stated in one breath, so
     * the second date is the end even when the word between them ("To") is too common to
     * treat as a label anywhere else.
     *
     * With nothing labelled, two or more dates are read as a period — earliest issued,
     * latest expiring — because a vehicle paper that prints a range is printing exactly
     * that. A **single** unlabelled date is left alone: it is as likely to be a print date
     * or a registration date as an expiry, and a wrong expiry is a reminder that fires on
     * the wrong day, which is worse than asking.
     */
    private fun datesIn(lines: List<String>): Dates {
        var issued: LocalDate? = null
        var expires: LocalDate? = null
        val unlabelled = mutableListOf<LocalDate>()

        lines.forEach { line ->
            val dates = DateReader.positioned(line)
            if (dates.isEmpty()) return@forEach
            val fromAt = FROM_HINT.find(line)?.range?.last
            val tillAt = TILL_HINT.find(line)?.range?.last

            when {
                // "Valid from X to Y" — one line, both ends, in print order.
                fromAt != null && dates.size >= 2 -> {
                    issued = issued ?: dates.first().second
                    expires = expires ?: dates.last().second
                }

                tillAt != null -> expires = expires ?: dates.after(tillAt)
                fromAt != null -> issued = issued ?: dates.after(fromAt)
                else -> unlabelled += dates.map { (_, date) -> date }
            }
        }

        if (expires == null && issued == null && unlabelled.size >= 2) {
            issued = unlabelled.min()
            expires = unlabelled.max()
        }
        return Dates(issuedOn = issued, expiresOn = expires)
    }

    /** The first date printed after [position], or the first on the line if none follows it. */
    private fun List<Pair<Int, LocalDate>>.after(position: Int): LocalDate? =
        (firstOrNull { (at, _) -> at > position } ?: firstOrNull())?.second

    /* ------------------------------ The label ------------------------------ */

    /**
     * A name to file the paper under — the insurer printed across the top of a policy.
     *
     * Only offered for insurance. Every other kind is already fully described by its type:
     * "Registration (RC)" is what an RC is, and the issuing authority's name on it ("Regional
     * Transport Office") tells the owner nothing they did not know. A policy is the one an
     * owner holds several of, from different insurers, so its name earns its place.
     *
     * Never a number: [ExtractedDocument] says a policy number must not travel, because
     * storing one turns every share of the document into a disclosure decision.
     */
    private fun titleIn(lines: List<String>, type: DocumentType?): String? {
        if (type != DocumentType.INSURANCE) return null
        return lines.take(HEADER_LINES).firstOrNull { line ->
            line.length in TITLE_LENGTH &&
                line.any { it.isLetter() } &&
                line.none { it.isDigit() } &&
                TITLE_NOISE.none { it.containsMatchIn(line) }
        }
    }

    /* ------------------------------ Confidence ------------------------------ */

    private fun confidence(
        hasType: Boolean,
        hasExpiry: Boolean,
        hasIssued: Boolean,
        hasTitle: Boolean,
    ): ExtractionConfidence {
        val score = listOf(
            hasType to TYPE_POINTS,
            hasExpiry to EXPIRY_POINTS,
            hasIssued to ISSUED_POINTS,
            hasTitle to TITLE_POINTS,
        ).sumOf { (present, points) -> if (present) points else 0 }
        return ExtractionConfidence.of(score.coerceAtMost(CONFIDENCE_CAP)).getOrNull()
            ?: ExtractionConfidence.NONE
    }

    private companion object {

        /**
         * What each kind of paper calls itself. Indian vehicle papers, as they are printed —
         * both spellings of "licence", and the abbreviations that appear alone on a form row.
         */
        val TYPE_WORDS: Map<DocumentType, List<Regex>> = mapOf(
            DocumentType.INSURANCE to words(
                "certificate of insurance", "insurance", "insurer", "policy no", "policy number",
                "premium", "idv", "insured declared value", "own damage", "third party",
                "cover note", "no claim bonus",
            ),
            DocumentType.PUC to words(
                "pollution under control", "puc", "pollution", "emission", "smoke density",
                "carbon monoxide",
            ),
            DocumentType.RC to words(
                "certificate of registration", "registration certificate", "regn no",
                "chassis no", "engine no", "fuel used", "unladen weight", "cubic capacity",
            ),
            DocumentType.LICENCE to words(
                "driving licence", "driving license", "licence to drive", "dl no",
                "date of birth", "blood group", "authorisation to drive",
            ),
        )

        /** The paper says its cover ends here. */
        val TILL_HINT = Regex(
            """\b(?:valid\s*(?:till|upto|up\s*to|until|to)|date\s*of\s*expiry|expiry|expires?|end\s*date|renewal\s*(?:date|due))\b""",
            RegexOption.IGNORE_CASE,
        )

        /** The paper says its cover starts here. */
        val FROM_HINT = Regex(
            """\b(?:valid\s*from|issued?\s*(?:on|date)|date\s*of\s*issue|start\s*date|w\.?e\.?f\.?|date\s*of\s*registration|regn\s*date)\b""",
            RegexOption.IGNORE_CASE,
        )

        /** How far down the paper a printed name can still be the issuer's. */
        const val HEADER_LINES = 4

        /** Long enough for "XYZ GENERAL INSURANCE CO. LTD", short enough to skip prose. */
        val TITLE_LENGTH = 3..56

        /**
         * Rows that are the form's own heading rather than anyone's name.
         *
         * Deliberately does **not** reject the word "insurance": the insurer is usually
         * called "… General Insurance Co. Ltd", and rejecting it would throw away the one
         * name worth keeping. What is rejected is the paper announcing itself —
         * "CERTIFICATE OF INSURANCE", "POLICY SCHEDULE" — and the issuing government rows.
         */
        val TITLE_NOISE = words(
            "certificate\\s*of", "certificate\\s*cum", "policy\\s*schedule", "schedule",
            "government\\s*of", "department", "form\\s*no",
        )

        /* Confidence weights — parse coverage, not accuracy. */
        const val TYPE_POINTS = 30
        const val EXPIRY_POINTS = 35
        const val ISSUED_POINTS = 10
        const val TITLE_POINTS = 5

        /** Below [ExtractionConfidence.HIGH] by design — a heuristic read is never trustworthy. */
        const val CONFIDENCE_CAP = 80

        /**
         * Each phrase as a word-bounded, space-tolerant pattern. OCR collapses and inserts
         * spaces freely, so "PUCCertificate" and "P U C" both have to reach the same rule.
         */
        fun words(vararg phrases: String): List<Regex> = phrases.map { phrase ->
            Regex("""\b${phrase.replace(" ", """\s*""")}\b""", RegexOption.IGNORE_CASE)
        }
    }
}
