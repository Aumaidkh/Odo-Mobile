package com.hopcape.odo.feature.servicelog.presentation.share.pdf

import com.hopcape.odo.core.designsystem.component.formatRegistrationNumber
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.shared.format
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatRupees

/**
 * One entry's bill, as one self-contained printed HTML document — the single-entry
 * counterpart of [ServiceRecordHtml], in the same print format: the running ODO header and
 * footer, the masthead, a strip of headline figures, and a lined table. Here the table is
 * the bill's own items rather than the car's timeline.
 *
 * Pure for the same reason as the record: a [ServiceRecord] (the car's identity), the
 * [ServiceLogEntry] being printed, resolved [ServiceBillLabels] and two embedded fonts in, a
 * string out. No resources, no clock, no platform.
 *
 * The layout rules are the record's, restated rather than shared: this document has its own
 * blocks and columns, and a common template bent to serve both would couple every future
 * change of one page to a re-read of the other.
 */
internal object ServiceBillHtml {

    fun build(
        record: ServiceRecord,
        entry: ServiceLogEntry,
        labels: ServiceBillLabels,
        fonts: ServiceRecordHtml.Fonts,
    ): String = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\">")
        append("<title>").append(labels.documentTitle(record.carName).escaped()).append("</title>")
        append("<style>").append(css(fonts)).append("</style>")
        append("</head><body>")

        append("<table class=\"doc\">")
        appendRunningHeader(record, labels)
        appendRunningFooter(labels)
        append("<tbody><tr><td>")
        appendMasthead(record, entry, labels)
        appendStats(entry, labels)
        appendProvenance(entry, labels)
        appendItems(entry, labels)
        append("<p class=\"disclaimer\">").append(labels.disclaimer.escaped()).append("</p>")
        append("</td></tr></tbody></table>")

        append("</body></html>")
    }

    /* ------------------------- running header and footer ------------------------- */

    private fun StringBuilder.appendRunningHeader(record: ServiceRecord, labels: ServiceBillLabels) {
        val identity = listOfNotNull(
            labels.headerPrefix,
            record.modelName.takeIf { it.isNotBlank() },
            record.registrationNumber?.let(::formatRegistrationNumber),
        ).joinToString(SEPARATOR)

        append("<thead><tr><td><div class=\"runner runner-top\">")
        append("<div><span class=\"brand\">ODO</span>")
        append("<span class=\"runner-text\">").append(identity.escaped()).append("</span></div>")
        append("<div class=\"runner-text\">")
        append(labels.issued(formatDate(record.issuedOn)).escaped())
        append("</div></div></td></tr></thead>")
    }

    private fun StringBuilder.appendRunningFooter(labels: ServiceBillLabels) {
        append("<tfoot><tr><td><div class=\"runner runner-bottom\">")
        append("<div class=\"footnote\">").append(labels.footer.escaped()).append("</div>")
        append("</div></td></tr></tfoot>")
    }

    /* ------------------------- masthead ------------------------- */

    private fun StringBuilder.appendMasthead(record: ServiceRecord, entry: ServiceLogEntry, labels: ServiceBillLabels) {
        val eyebrow = if (entry.isVerified) labels.eyebrow else labels.eyebrowSelfReported
        append("<div class=\"eyebrow\">").append(eyebrow.escaped()).append("</div>")
        append("<h1 class=\"title\">").append(record.carName.escaped()).append("</h1>")

        val subtitle = listOfNotNull(
            record.modelYear?.toString(),
            record.fuelType?.let(labels.fuelName),
            record.registrationNumber?.let(::formatRegistrationNumber),
            // The reading the bill was raised at, not the car's reading today — this page is
            // about one visit to a workshop.
            entry.odometer.readable(),
        ).joinToString(SEPARATOR)
        append("<div class=\"subtitle\">").append(subtitle.escaped()).append("</div>")
        append("<div class=\"rule-strong\"></div>")
    }

    /* ------------------------- the four figures ------------------------- */

    private fun StringBuilder.appendStats(entry: ServiceLogEntry, labels: ServiceBillLabels) {
        append("<table class=\"stats\"><tr>")
        appendStat(labels.statDate, formatDate(entry.serviceDate))
        appendStat(labels.statOdometer, entry.odometer.readable())
        appendStat(
            label = labels.statStatus,
            value = if (entry.isVerified) labels.statusVerified else labels.statusSelfReported,
            valueClass = if (entry.isVerified) " ok" else " muted",
        )
        appendStat(labels.statTotal, entry.totalAmount.formatRupees())
        append("</tr></table>")
    }

    private fun StringBuilder.appendStat(label: String, value: String, valueClass: String = "") {
        append("<td class=\"stat\">")
        append("<div class=\"stat-label\">").append(label.escaped()).append("</div>")
        append("<div class=\"stat-value$valueClass\">").append(value.escaped()).append("</div></td>")
    }

    /* ------------------------- where and what for ------------------------- */

    private fun StringBuilder.appendProvenance(entry: ServiceLogEntry, labels: ServiceBillLabels) {
        val workshop = entry.workshopName?.value
        val notes = entry.notes?.value
        if (workshop == null && notes == null) return

        append("<table class=\"kv provenance\">")
        workshop?.let { appendPair(labels.workshop, it) }
        notes?.let { appendPair(labels.notes, it) }
        append("</table>")
    }

    private fun StringBuilder.appendPair(key: String, value: String) {
        append("<tr><td class=\"k\">").append(key.escaped()).append("</td>")
        append("<td class=\"v\">").append(value.escaped()).append("</td></tr>")
    }

    /* ------------------------- the billed items ------------------------- */

    private fun StringBuilder.appendItems(entry: ServiceLogEntry, labels: ServiceBillLabels) {
        append("<div class=\"rule\"></div>")
        append("<div class=\"block-label\">").append(labels.items.escaped()).append("</div>")

        append("<table class=\"items\"><thead><tr>")
        append("<th class=\"c-item\">").append(labels.columnItem.escaped()).append("</th>")
        append("<th class=\"c-category\">").append(labels.columnCategory.escaped()).append("</th>")
        append("<th class=\"c-amount\">").append(labels.columnAmount.escaped()).append("</th>")
        append("</tr></thead><tbody>")
        entry.lineItems.forEach { item -> appendItem(item, labels) }
        // An entry logged without a priced breakdown still prints what it was for: its
        // category tags, or the bare word for a service, each without an amount rather than
        // with the total repeated against a line it does not belong to.
        if (entry.lineItems.isEmpty()) {
            val jobs = entry.categories.map(labels.categoryName).ifEmpty { listOf(labels.workUnspecified) }
            jobs.forEach { job ->
                append("<tr><td class=\"c-item\"><div class=\"work\">").append(job.escaped()).append("</div></td>")
                append("<td class=\"c-category\">").append(EMPTY_FIELD).append("</td>")
                append("<td class=\"c-amount\">").append(EMPTY_FIELD).append("</td></tr>")
            }
        }
        append("</tbody></table>")

        // The entry's own total, printed from the aggregate's authoritative figure — the
        // lines are a breakdown and are not forced to sum to it.
        append("<div class=\"total\">")
        append("<span>").append(labels.total.escaped()).append("</span>")
        append("<span class=\"total-amount\">").append(entry.totalAmount.formatRupees().escaped()).append("</span>")
        append("</div>")
    }

    private fun StringBuilder.appendItem(item: ServiceLogLineItem, labels: ServiceBillLabels) {
        append("<tr>")
        append("<td class=\"c-item\"><div class=\"work\">")
        // A scanned line keeps the bill's own wording; a line entered without one falls back
        // to its category so no row prints blank.
        append((item.label ?: labels.categoryName(item.category)).escaped())
        append("</div></td>")
        append("<td class=\"c-category\">").append(labels.categoryName(item.category).escaped()).append("</td>")
        append("<td class=\"c-amount\">").append(item.amount.formatRupees().escaped()).append("</td>")
        append("</tr>")
    }

    /* ------------------------- helpers ------------------------- */

    private val ServiceLogEntry.isVerified: Boolean
        get() = verification == VerificationStatus.VERIFIED

    /** "54,120 km" — kilometres, because Odo's records are Indian. */
    private fun Distance.readable(): String = format(DistanceUnit.KILOMETRE)

    private const val SEPARATOR = " · "
    private const val EMPTY_FIELD = "—"

    /** Reserved so a workshop called `Sharma <Motors>` cannot rewrite the document around it. */
    private fun String.escaped(): String = buildString(length) {
        this@escaped.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }

    /* ------------------------- style ------------------------- */

    private fun css(fonts: ServiceRecordHtml.Fonts): String =
        """
        @font-face {
          font-family: 'Odo Sans';
          src: url(data:font/ttf;base64,${fonts.regularBase64}) format('truetype');
          font-weight: 400; font-style: normal; font-display: block;
        }
        @font-face {
          font-family: 'Odo Sans';
          src: url(data:font/ttf;base64,${fonts.boldBase64}) format('truetype');
          font-weight: 700; font-style: normal; font-display: block;
        }
        @page { size: A4; margin: 13mm 14mm; }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; }
        body {
          font-family: 'Odo Sans', sans-serif;
          font-size: 8.5pt; line-height: 1.4; color: $INK;
          -webkit-print-color-adjust: exact; print-color-adjust: exact;
        }
        table { border-collapse: collapse; width: 100%; }
        td, th { padding: 0; vertical-align: top; }

        .runner { display: flex; justify-content: space-between; align-items: baseline; }
        .runner-top { padding-bottom: 2mm; margin-bottom: 4mm; border-bottom: 0.4pt solid $LINE; }
        .runner-bottom { padding-top: 2mm; margin-top: 4mm; border-top: 0.4pt solid $LINE; }
        .brand { font-weight: 700; letter-spacing: 0.09em; font-size: 8pt; margin-right: 2mm; }
        .runner-text { color: $DIM; font-size: 7pt; }
        .footnote { color: $MUTED; font-size: 6.5pt; line-height: 1.35; }

        .eyebrow {
          text-transform: uppercase; letter-spacing: 0.13em;
          font-size: 6.5pt; font-weight: 700; color: $DIM;
        }
        .title { font-size: 23pt; font-weight: 700; margin: 1mm 0 0.5mm; letter-spacing: -0.01em; }
        .subtitle { color: $DIM; font-size: 8.5pt; }
        .rule-strong { border-top: 1.1pt solid $INK; margin: 4mm 0 0; }
        .rule { border-top: 0.4pt solid $LINE; margin: 4mm 0; }

        .stats { table-layout: fixed; }
        .stat {
          padding: 3.5mm 0 3.5mm 4mm;
          border-left: 0.4pt solid $LINE; border-bottom: 0.4pt solid $LINE;
        }
        .stat:first-child { padding-left: 0; border-left: 0; }
        .stat-label {
          text-transform: uppercase; letter-spacing: 0.11em;
          font-size: 6pt; font-weight: 700; color: $DIM; margin-bottom: 1mm;
        }
        .stat-value { font-size: 13pt; font-weight: 700; letter-spacing: -0.01em; }

        .provenance { margin-top: 4.5mm; }
        .kv td { padding: 0.9mm 0; font-size: 8pt; }
        .kv .k { color: $DIM; width: 17%; }
        .kv .v { font-weight: 700; }
        .block-label {
          text-transform: uppercase; letter-spacing: 0.11em;
          font-size: 6pt; font-weight: 700; color: $DIM; margin-bottom: 1.6mm;
        }

        .items { margin-top: 1mm; }
        .items th {
          text-transform: uppercase; letter-spacing: 0.1em;
          font-size: 5.8pt; font-weight: 700; color: $DIM;
          text-align: left; padding-bottom: 1.6mm; border-bottom: 0.4pt solid $LINE;
        }
        .items td { padding: 2.4mm 0; border-bottom: 0.4pt solid $LINE; font-size: 8pt; }
        .items tr { page-break-inside: avoid; break-inside: avoid; }
        .c-item { width: 54%; padding-right: 4mm; }
        .c-category { width: 26%; padding-right: 2mm; color: $DIM; }
        .c-amount { width: 20%; text-align: right; font-weight: 700; }
        th.c-amount { text-align: right; }
        .work { font-weight: 700; }
        .ok { color: $GREEN; font-weight: 700; }
        .muted { color: $DIM; }

        .total {
          display: flex; justify-content: space-between;
          font-weight: 700; font-size: 9.5pt; padding-top: 3mm;
        }
        .total-amount { font-size: 11pt; }

        .disclaimer { color: $MUTED; font-size: 6.8pt; line-height: 1.4; margin-top: 4mm; }
        """.trimIndent()

    // The light palette, hardcoded — the same trade as the record: paper has one appearance.
    private const val INK = "#1F1813"
    private const val DIM = "#6F6457"
    private const val MUTED = "#A99D8C"
    private const val LINE = "#E6DCCF"
    private const val GREEN = "#0F9D63"
}
