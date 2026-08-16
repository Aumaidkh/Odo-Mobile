package com.hopcape.odo.feature.servicelog.presentation.share.pdf

import com.hopcape.odo.core.designsystem.component.formatRegistrationNumber
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.record.model.DocumentOnFile
import com.hopcape.odo.core.domain.record.model.RecordRow
import com.hopcape.odo.core.domain.record.model.RecordStatus
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.servicelog.model.WorkDone
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.shared.format
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatMonthYear
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.suffix

/**
 * The printed service record, as one self-contained HTML document.
 *
 * Pure: a [ServiceRecord], its already-resolved [ServiceRecordLabels] and two embedded fonts
 * in, a string out. No resources, no clock, no platform — which is what makes the whole
 * layout testable on the host JVM.
 *
 * The document is written for print rather than for a screen. Sizes are in points and
 * millimetres because it is going onto A4; the palette is the light one, hardcoded, because
 * paper has no dark mode and a dark-mode PDF is a printer full of toner.
 *
 * **Running header and footer are a `<thead>` and `<tfoot>`.** Every part of the document
 * sits inside one outer table, so both engines repeat them on each page for free. A
 * `position: fixed` block is the obvious way to do this and behaves differently in each
 * engine; a table header is what both were built to repeat.
 *
 * Nothing is fetched. Fonts arrive base64-encoded and inline, there is no image and no
 * script, because the owner shares this from wherever they happen to be standing.
 */
internal object ServiceRecordHtml {

    /** The brand face, base64-encoded, ready to inline as a `data:` URI. */
    internal data class Fonts(val regularBase64: String, val boldBase64: String)

    fun build(record: ServiceRecord, labels: ServiceRecordLabels, fonts: Fonts): String = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\">")
        append("<title>").append(labels.documentTitle(record.carName).escaped()).append("</title>")
        append("<style>").append(css(fonts)).append("</style>")
        append("</head><body>")

        append("<table class=\"doc\">")
        appendRunningHeader(record, labels)
        appendRunningFooter(labels)
        append("<tbody><tr><td>")
        appendMasthead(record, labels)
        appendStats(record, labels)
        appendBlocks(record, labels)
        appendTimeline(record, labels)
        appendHowToRead(labels)
        append("<p class=\"disclaimer\">").append(labels.disclaimer.escaped()).append("</p>")
        append("</td></tr></tbody></table>")

        append("</body></html>")
    }

    /* ------------------------- running header and footer ------------------------- */

    private fun StringBuilder.appendRunningHeader(record: ServiceRecord, labels: ServiceRecordLabels) {
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

    private fun StringBuilder.appendRunningFooter(labels: ServiceRecordLabels) {
        append("<tfoot><tr><td><div class=\"runner runner-bottom\">")
        append("<div class=\"footnote\">").append(labels.footer.escaped()).append("</div>")
        append("</div></td></tr></tfoot>")
    }

    /* ------------------------- masthead ------------------------- */

    private fun StringBuilder.appendMasthead(record: ServiceRecord, labels: ServiceRecordLabels) {
        append("<div class=\"eyebrow\">").append(labels.eyebrow.escaped()).append("</div>")
        append("<h1 class=\"title\">").append(record.carName.escaped()).append("</h1>")

        val subtitle = listOfNotNull(
            record.modelYear?.toString(),
            record.fuelType?.let(labels.fuelName),
            record.registrationNumber?.let(::formatRegistrationNumber),
            record.odometer?.readable(),
        ).joinToString(SEPARATOR)
        if (subtitle.isNotEmpty()) {
            append("<div class=\"subtitle\">").append(subtitle.escaped()).append("</div>")
        }
        append("<div class=\"rule-strong\"></div>")
    }

    /* ------------------------- the four figures ------------------------- */

    private fun StringBuilder.appendStats(record: ServiceRecord, labels: ServiceRecordLabels) {
        append("<table class=\"stats\"><tr>")
        appendStat(
            label = labels.statHealth,
            // A car nobody has scored prints a dash rather than a zero, which would read as
            // a car that scored nothing.
            value = record.healthScore?.toString() ?: EMPTY_FIELD,
            unit = labels.statHealthUnit.takeIf { record.healthScore != null },
        )
        appendStat(label = labels.statEntries, value = record.entryCount.toString(), unit = null)
        appendStat(
            label = labels.statVerified,
            value = record.verifiedCount.toString(),
            unit = labels.statVerifiedOf(record.entryCount),
        )
        appendStat(
            label = labels.statSince,
            value = record.recordSince?.let(::formatMonthYear) ?: EMPTY_FIELD,
            unit = null,
        )
        append("</tr></table>")
    }

    private fun StringBuilder.appendStat(label: String, value: String, unit: String?) {
        append("<td class=\"stat\">")
        append("<div class=\"stat-label\">").append(label.escaped()).append("</div>")
        append("<div class=\"stat-value\">").append(value.escaped())
        if (unit != null) append("<span class=\"stat-unit\">").append(unit.escaped()).append("</span>")
        append("</div></td>")
    }

    /* ------------------------- ownership and documents ------------------------- */

    private fun StringBuilder.appendBlocks(record: ServiceRecord, labels: ServiceRecordLabels) {
        append("<table class=\"cols\"><tr>")

        append("<td class=\"col\">")
        append("<div class=\"block-label\">").append(labels.ownership.escaped()).append("</div>")
        append("<table class=\"kv\">")
        record.ownership.ownerName?.let { appendPair(labels.owner, it) }
        record.ownership.ownedSince?.let { appendPair(labels.ownedSince, it.toString()) }
        // Only claimed once something has been logged: "consistent across all entries" said
        // of a car with no entries is a boast about nothing.
        if (record.rows.any { it.event is ActivityEvent.Service }) {
            appendPair(
                key = labels.odometer,
                value = if (record.ownership.odometerConsistent) {
                    labels.odometerConsistent
                } else {
                    labels.odometerInconsistent
                },
            )
        }
        append("</table></td>")

        append("<td class=\"col\">")
        if (record.documents.isNotEmpty()) {
            append("<div class=\"block-label\">").append(labels.documents.escaped()).append("</div>")
            append("<table class=\"kv\">")
            record.documents.forEach { document -> appendDocument(document, labels) }
            append("</table>")
        }
        append("</td>")

        append("</tr></table>")
        append("<div class=\"rule\"></div>")
    }

    private fun StringBuilder.appendPair(key: String, value: String) {
        append("<tr><td class=\"k\">").append(key.escaped()).append("</td>")
        append("<td class=\"v\">").append(value.escaped()).append("</td>")
        append("<td class=\"tick\"></td></tr>")
    }

    private fun StringBuilder.appendDocument(document: DocumentOnFile, labels: ServiceRecordLabels) {
        val state = when (val validity = document.validity) {
            DocumentValidity.NoExpiry -> labels.onFile
            is DocumentValidity.Valid -> labels.validTill(formatDate(validity.until))
            is DocumentValidity.ExpiringSoon -> labels.validTill(formatDate(validity.until))
            is DocumentValidity.Expired -> labels.expired(formatDate(validity.since))
        }

        append("<tr><td class=\"k\">").append(labels.documentName(document.type).escaped()).append("</td>")
        append("<td class=\"v\">").append(state.escaped()).append("</td>")
        // Ticked only while the paper is in force. An expired policy is on file and is still
        // expired, and a tick beside it would read as "this car is covered".
        append("<td class=\"tick\">")
        if (document.validity.isInForce) append("<span class=\"ok\">$TICK</span>")
        append("</td></tr>")
    }

    /* ------------------------- the entries ------------------------- */

    private fun StringBuilder.appendTimeline(record: ServiceRecord, labels: ServiceRecordLabels) {
        append("<div class=\"block-label\">").append(labels.timeline.escaped()).append("</div>")

        if (record.isEmpty) {
            append("<p class=\"empty\">").append(labels.empty.escaped()).append("</p>")
            return
        }

        append("<table class=\"timeline\"><thead><tr>")
        append("<th class=\"c-date\">").append(labels.columnDate.escaped()).append("</th>")
        append("<th class=\"c-odo\">").append(labels.columnOdometer.escaped()).append("</th>")
        append("<th class=\"c-work\">").append(labels.columnWork.escaped()).append("</th>")
        append("<th class=\"c-status\">").append(labels.columnStatus.escaped()).append("</th>")
        append("<th class=\"c-amount\">").append(labels.columnAmount.escaped()).append("</th>")
        append("</tr></thead><tbody>")
        record.rows.forEach { row -> appendRow(row, labels) }
        append("</tbody></table>")

        append("<div class=\"total\">")
        append("<span>").append(labels.total.escaped()).append("</span>")
        append("<span class=\"total-amount\">").append(record.total.formatRupees().escaped()).append("</span>")
        append("</div>")
    }

    private fun StringBuilder.appendRow(row: RecordRow, labels: ServiceRecordLabels) {
        append("<tr>")
        append("<td class=\"c-date\">").append(formatDate(row.date).escaped()).append("</td>")
        append("<td class=\"c-odo\">").append((row.odometer?.readableNumber() ?: EMPTY_FIELD).escaped()).append("</td>")

        append("<td class=\"c-work\">")
        // One line per job. A scanned bill can carry a dozen items, and joining them into a
        // single run-on sentence is unreadable at exactly the moment the record matters —
        // a buyer scanning down the page for what was actually done.
        row.jobs(labels).forEach { job ->
            append("<div class=\"work\">").append(job.escaped()).append("</div>")
        }
        row.subtitle(labels)?.let { append("<div class=\"where\">").append(it.escaped()).append("</div>") }
        append("</td>")

        val statusClass = if (row.status == RecordStatus.VERIFIED) "ok" else "muted"
        val statusText = if (row.status == RecordStatus.VERIFIED) labels.statusVerified else labels.statusSelfReported
        append("<td class=\"c-status $statusClass\">").append(statusText.escaped()).append("</td>")

        append("<td class=\"c-amount\">").append((row.amount?.formatRupees() ?: EMPTY_FIELD).escaped()).append("</td>")
        append("</tr>")
    }

    /**
     * What a printed line says happened — one entry per job, so a bill with ten items reads
     * as ten lines rather than as one paragraph.
     *
     * Never empty: an entry with nothing recorded against it still prints something, because
     * a blank cell in the middle of a record looks like a document that failed to render.
     */
    private fun RecordRow.jobs(labels: ServiceRecordLabels): List<String> = when (val event = event) {
        is ActivityEvent.Service -> event.workDone.describe(labels)
        is ActivityEvent.DocumentFiled -> {
            val name = labels.documentName(event.document)
            listOf(if (event.isRenewal) labels.documentRenewed(name) else labels.documentAdded(name))
        }
        is ActivityEvent.CarAdded -> listOf(labels.carAdded)
        // Neither is ever printed: the builder drops score moves, and it is never given the
        // fills at all — a resale record is an account of how the car was looked after, and a
        // year of refuelling would bury the services that say so.
        is ActivityEvent.ScoreChanged -> listOf(labels.carAdded)
        is ActivityEvent.FuelFilled -> listOf(labels.carAdded)
    }

    /** The line under the headline: where the work was done, or when a paper runs out. */
    private fun RecordRow.subtitle(labels: ServiceRecordLabels): String? = when (val event = event) {
        is ActivityEvent.Service -> event.workshop?.value
        is ActivityEvent.DocumentFiled -> event.validTill?.let { labels.validTill(formatDate(it)) }
        else -> null
    }

    /**
     * The jobs a service covered, one per line. The parameter is named [wording] rather than
     * `labels` because [WorkDone.Described] has a `labels` of its own — the owner's own words,
     * or the line items read off a scanned bill.
     */
    private fun WorkDone.describe(wording: ServiceRecordLabels): List<String> = when (this) {
        WorkDone.Unspecified -> listOf(wording.workUnspecified)
        // A single free-text note that the owner wrote as one sentence stays one line; a
        // scanned bill arrives already split into its items and prints as those items.
        is WorkDone.Described -> labels.ifEmpty { listOf(wording.workUnspecified) }
        is WorkDone.Tagged -> categories
            .map { wording.categoryName(it) }
            .ifEmpty { listOf(wording.workUnspecified) }
    }

    /* ------------------------- the legend ------------------------- */

    private fun StringBuilder.appendHowToRead(labels: ServiceRecordLabels) {
        append("<div class=\"howto\">")
        append("<div class=\"block-label\">").append(labels.howToRead.escaped()).append("</div>")
        append("<table class=\"kv\">")
        appendLegend("ok", labels.statusVerified, labels.howToReadVerified)
        appendLegend("muted", labels.statusSelfReported, labels.howToReadSelfReported)
        appendLegend("strong", labels.odometer, labels.howToReadOdometer)
        append("</table></div>")
    }

    private fun StringBuilder.appendLegend(styleClass: String, term: String, explanation: String) {
        append("<tr><td class=\"k $styleClass\">").append(term.escaped()).append("</td>")
        append("<td class=\"v legend\" colspan=\"2\">").append(explanation.escaped()).append("</td></tr>")
    }

    /* ------------------------- helpers ------------------------- */

    /** "54,120 km" — kilometres, because Odo's records are Indian. */
    private fun Distance.readable(): String = format(DistanceUnit.KILOMETRE)

    /** "54,120" — the odometer column, where the unit is already in the heading. */
    private fun Distance.readableNumber(): String =
        readable().removeSuffix(" ${DistanceUnit.KILOMETRE.suffix()}")

    private const val SEPARATOR = " · "
    private const val EMPTY_FIELD = "—"
    private const val TICK = "✓"

    /** Reserved so a car called `Swift <VXI>` cannot rewrite the document around it. */
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

    private fun css(fonts: Fonts): String =
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
        .stat-value { font-size: 15pt; font-weight: 700; letter-spacing: -0.01em; }
        .stat-unit { font-size: 7.5pt; font-weight: 400; color: $DIM; margin-left: 0.8mm; }

        .cols { table-layout: fixed; margin-top: 4.5mm; }
        .col { width: 50%; padding-right: 6mm; }
        .block-label {
          text-transform: uppercase; letter-spacing: 0.11em;
          font-size: 6pt; font-weight: 700; color: $DIM; margin-bottom: 1.6mm;
        }
        .kv td { padding: 0.9mm 0; font-size: 8pt; }
        .kv .k { color: $DIM; width: 34%; }
        .kv .v { font-weight: 700; }
        .kv .tick { width: 6mm; text-align: right; }
        .kv .legend { font-weight: 400; color: $INK; line-height: 1.4; }
        .kv .strong { color: $INK; font-weight: 700; }

        .timeline { margin-top: 1mm; }
        .timeline th {
          text-transform: uppercase; letter-spacing: 0.1em;
          font-size: 5.8pt; font-weight: 700; color: $DIM;
          text-align: left; padding-bottom: 1.6mm; border-bottom: 0.4pt solid $LINE;
        }
        .timeline td { padding: 2.4mm 0; border-bottom: 0.4pt solid $LINE; font-size: 8pt; }
        .timeline tr { page-break-inside: avoid; break-inside: avoid; }
        .c-date { width: 17%; padding-right: 2mm; color: $INK; }
        .c-odo { width: 11%; padding-right: 2mm; color: $INK; }
        .c-work { width: 42%; padding-right: 4mm; }
        .c-status { width: 14%; padding-right: 2mm; }
        .c-amount { width: 16%; text-align: right; font-weight: 700; }
        th.c-amount { text-align: right; }
        .work { font-weight: 700; }
        .work + .work { margin-top: 0.7mm; }
        .where { color: $DIM; font-size: 7.5pt; }
        .ok { color: $GREEN; font-weight: 700; }
        .muted { color: $DIM; }

        .total {
          display: flex; justify-content: space-between;
          font-weight: 700; font-size: 9.5pt; padding-top: 3mm;
        }
        .total-amount { font-size: 11pt; }

        .howto {
          margin-top: 6mm; padding: 4mm; border: 0.4pt solid $LINE; border-radius: 2mm;
          page-break-inside: avoid; break-inside: avoid;
        }
        .howto .k { width: 22%; }
        .empty { color: $DIM; font-size: 8pt; }
        .disclaimer { color: $MUTED; font-size: 6.8pt; line-height: 1.4; margin-top: 4mm; }
        """.trimIndent()

    // The light palette, hardcoded. This is paper: it has one appearance, and a document that
    // came out of dark mode is a document nobody can read on a printer.
    private const val INK = "#1F1813"
    private const val DIM = "#6F6457"
    private const val MUTED = "#A99D8C"
    private const val LINE = "#E6DCCF"
    private const val GREEN = "#0F9D63"
}
