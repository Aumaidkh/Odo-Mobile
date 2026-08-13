package com.hopcape.odo.feature.garage.presentation.sheets.pdf

import com.hopcape.odo.core.designsystem.component.formatRegistrationNumber
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.cost.model.CostBreakdown
import com.hopcape.odo.core.domain.cost.model.RunningCost
import com.hopcape.odo.core.domain.cost.model.SpendCategory
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.record.model.RecordRow
import com.hopcape.odo.core.domain.record.model.RecordStatus
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.shared.format
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.domain.shared.formatRupeesDecimal
import com.hopcape.odo.core.domain.shared.suffix
import com.hopcape.odo.feature.garage.domain.usecase.CarDetails
import com.hopcape.odo.feature.garage.domain.usecase.CarDetailsDocument

/**
 * The printed vehicle details, as one self-contained HTML document — the garage's member
 * of the print family the service log started ("ServiceRecordHtml", "ServiceBillHtml"),
 * in the same format: the running ODO header and footer, the masthead, a strip of headline
 * figures, and lined tables.
 *
 * Pure for the same reason as the other two: a [CarDetails], its resolved
 * [CarDetailsLabels] and two embedded fonts in, a string out. No resources, no clock, no
 * platform — which is what makes the whole layout testable on the host JVM.
 *
 * What the reference design asks for but Odo has never collected — a VIN, an engine
 * number, a transmission, an RTO, an accident declaration — is absent rather than printed
 * blank: a dash against "VIN" invites the reader to ask why the owner hid it, when the
 * truth is the app never asked.
 */
internal object CarDetailsHtml {

    /** The brand face, base64-encoded, ready to inline as a `data:` URI. */
    internal data class Fonts(val regularBase64: String, val boldBase64: String)

    fun build(details: CarDetails, labels: CarDetailsLabels, fonts: Fonts): String = buildString {
        val record = details.record

        append("<!doctype html><html><head><meta charset=\"utf-8\">")
        append("<title>").append(labels.documentTitle(record.carName).escaped()).append("</title>")
        append("<style>").append(css(fonts)).append("</style>")
        append("</head><body>")

        append("<table class=\"doc\">")
        appendRunningHeader(record, labels)
        appendRunningFooter(labels)
        append("<tbody><tr><td>")
        appendMasthead(record, labels)
        appendStats(details, labels)
        appendIdentification(record, labels)
        appendOwnership(details, labels)
        appendDocuments(details.documents, labels)
        appendRunningCosts(details.runningCost, labels)
        appendHealth(details, labels)
        appendServiceSummary(record, labels)
        appendHowToRead(labels)
        append("<p class=\"disclaimer\">").append(labels.disclaimer.escaped()).append("</p>")
        append("</td></tr></tbody></table>")

        append("</body></html>")
    }

    /* ------------------------- running header and footer ------------------------- */

    private fun StringBuilder.appendRunningHeader(record: ServiceRecord, labels: CarDetailsLabels) {
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

    private fun StringBuilder.appendRunningFooter(labels: CarDetailsLabels) {
        append("<tfoot><tr><td><div class=\"runner runner-bottom\">")
        append("<div class=\"footnote\">").append(labels.footer.escaped()).append("</div>")
        append("</div></td></tr></tfoot>")
    }

    /* ------------------------- masthead ------------------------- */

    private fun StringBuilder.appendMasthead(record: ServiceRecord, labels: CarDetailsLabels) {
        append("<div class=\"eyebrow\">").append(labels.eyebrow.escaped()).append("</div>")
        append("<h1 class=\"title\">").append(record.carName.escaped()).append("</h1>")

        val subtitle = listOfNotNull(
            record.modelYear?.toString(),
            record.fuelType?.let(labels.fuelName),
            record.registrationNumber?.let(::formatRegistrationNumber),
        ).joinToString(SEPARATOR)
        if (subtitle.isNotEmpty()) {
            append("<div class=\"subtitle\">").append(subtitle.escaped()).append("</div>")
        }
        append("<div class=\"rule-strong\"></div>")
    }

    /* ------------------------- the four figures ------------------------- */

    private fun StringBuilder.appendStats(details: CarDetails, labels: CarDetailsLabels) {
        val record = details.record
        append("<table class=\"stats\"><tr>")
        appendStat(
            label = labels.statOdometer,
            value = record.odometer?.readableNumber() ?: EMPTY_FIELD,
            unit = KM_SUFFIX.takeIf { record.odometer != null },
        )
        val age = record.age()
        appendStat(
            label = labels.statAge,
            value = age?.toString() ?: EMPTY_FIELD,
            unit = age?.let(labels.ageUnit),
        )
        appendStat(
            label = labels.statHealth,
            // A car nobody has scored prints a dash rather than a zero, which would read
            // as a car that scored nothing.
            value = record.healthScore?.toString() ?: EMPTY_FIELD,
            unit = labels.statHealthUnit.takeIf { record.healthScore != null },
        )
        val perKm = details.runningCost?.perKm
        appendStat(
            label = labels.statCost,
            // No rate below the distance threshold — the calculator refused the false
            // precision, and so does the page.
            value = perKm?.formatRupeesDecimal() ?: EMPTY_FIELD,
            unit = labels.statCostUnit.takeIf { perKm != null },
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

    /* ------------------------- identification and ownership ------------------------- */

    private fun StringBuilder.appendIdentification(record: ServiceRecord, labels: CarDetailsLabels) {
        append("<div class=\"section\">")
        append("<div class=\"block-label\">").append(labels.identification.escaped()).append("</div>")
        append("<table class=\"cols\"><tr>")

        append("<td class=\"col\"><table class=\"kv\">")
        record.registrationNumber?.let { appendPair(labels.registration, formatRegistrationNumber(it)) }
        record.modelName.takeIf { it.isNotBlank() }?.let { appendPair(labels.makeModel, it) }
        append("</table></td>")

        append("<td class=\"col\"><table class=\"kv\">")
        record.modelYear?.let { appendPair(labels.modelYear, it.toString()) }
        record.fuelType?.let { appendPair(labels.fuel, labels.fuelName(it)) }
        append("</table></td>")

        append("</tr></table></div>")
    }

    private fun StringBuilder.appendOwnership(details: CarDetails, labels: CarDetailsLabels) {
        val record = details.record
        append("<div class=\"section\">")
        append("<div class=\"block-label\">").append(labels.ownership.escaped()).append("</div>")
        append("<table class=\"cols\"><tr>")

        append("<td class=\"col\"><table class=\"kv\">")
        record.ownership.ownerName?.let { appendPair(labels.owner, it) }
        record.ownership.ownedSince?.let { appendPair(labels.ownedSince, it.toString()) }
        append("</table></td>")

        append("<td class=\"col\"><table class=\"kv\">")
        details.city?.let { appendPair(labels.cityOfUse, it) }
        // Only claimed once something has been logged: "consistent" said of a car with no
        // entries would be a boast about nothing — the record's own rule, restated here.
        if (record.rows.any { it.event is ActivityEvent.Service }) {
            appendPair(
                key = labels.odometerTrail,
                value = if (record.ownership.odometerConsistent) {
                    labels.trailConsistent
                } else {
                    labels.trailInconsistent
                },
            )
        }
        append("</table></td>")

        append("</tr></table></div>")
    }

    private fun StringBuilder.appendPair(key: String, value: String) {
        append("<tr><td class=\"k\">").append(key.escaped()).append("</td>")
        append("<td class=\"v\">").append(value.escaped()).append("</td></tr>")
    }

    /* ------------------------- the papers ------------------------- */

    private fun StringBuilder.appendDocuments(papers: List<CarDetailsDocument>, labels: CarDetailsLabels) {
        // A heading over nothing is noise — an empty vault drops the section.
        if (papers.isEmpty()) return

        append("<div class=\"section\">")
        append("<div class=\"block-label\">").append(labels.documents.escaped()).append("</div>")
        append("<table class=\"lined\"><thead><tr>")
        append("<th class=\"c-doc\">").append(labels.columnDocument.escaped()).append("</th>")
        append("<th class=\"c-detail\">").append(labels.columnDetails.escaped()).append("</th>")
        append("<th class=\"c-till\">").append(labels.columnValidTill.escaped()).append("</th>")
        append("<th class=\"c-state\">").append(labels.columnStatus.escaped()).append("</th>")
        append("</tr></thead><tbody>")
        papers.forEach { paper -> appendDocument(paper, labels) }
        append("</tbody></table></div>")
    }

    private fun StringBuilder.appendDocument(paper: CarDetailsDocument, labels: CarDetailsLabels) {
        val till = when (val validity = paper.validity) {
            DocumentValidity.NoExpiry -> null
            is DocumentValidity.Valid -> validity.until
            is DocumentValidity.ExpiringSoon -> validity.until
            is DocumentValidity.Expired -> validity.since
        }
        val state = when (paper.validity) {
            DocumentValidity.NoExpiry -> labels.statusOnFile
            is DocumentValidity.Valid -> labels.statusValid
            is DocumentValidity.ExpiringSoon -> labels.statusExpiring
            is DocumentValidity.Expired -> labels.statusExpired
        }
        // Green only while the paper is in force — "Expired" in green would read as cover.
        val stateClass = if (paper.validity.isInForce) "ok" else "bad"

        append("<tr>")
        append("<td class=\"c-doc strong\">").append(labels.documentName(paper.type).escaped()).append("</td>")
        append("<td class=\"c-detail\">").append((paper.title ?: EMPTY_FIELD).escaped()).append("</td>")
        append("<td class=\"c-till\">").append((till?.let(::formatDate) ?: EMPTY_FIELD).escaped()).append("</td>")
        append("<td class=\"c-state $stateClass\">").append(state.escaped()).append("</td>")
        append("</tr>")
    }

    /* ------------------------- running costs ------------------------- */

    private fun StringBuilder.appendRunningCosts(cost: RunningCost?, labels: CarDetailsLabels) {
        // Nothing spent and nothing driven is not a cost table; the section waits for data
        // rather than printing a page of zeros.
        if (cost == null || cost.totalSpend.paise == 0L) return

        append("<div class=\"section\">")
        append("<div class=\"block-label\">").append(labels.costs.escaped()).append("</div>")
        append("<table class=\"lined\"><thead><tr>")
        append("<th class=\"c-cat\">").append(labels.columnCategory.escaped()).append("</th>")
        append("<th class=\"c-num\">").append(labels.columnAmount.escaped()).append("</th>")
        append("<th class=\"c-num\">").append(labels.columnPerKm.escaped()).append("</th>")
        append("<th class=\"c-num\">").append(labels.columnShare.escaped()).append("</th>")
        append("</tr></thead><tbody>")
        cost.categories.filter { it.spend.paise > 0 }.forEach { row -> appendCostRow(row, cost, labels) }
        append("</tbody></table>")

        append("<div class=\"total\"><span>")
        append(labels.costTotal(cost.kmDriven.format(DistanceUnit.KILOMETRE)).escaped())
        append("</span><span class=\"total-amount\">").append(cost.totalSpend.formatRupees().escaped()).append("</span></div>")
        append("</div>")
    }

    private fun StringBuilder.appendCostRow(row: CostBreakdown, cost: RunningCost, labels: CarDetailsLabels) {
        val share = (row.spend.paise * 100 + cost.totalSpend.paise / 2) / cost.totalSpend.paise

        append("<tr>")
        append("<td class=\"c-cat strong\">").append(labels.costCategory(row.category).escaped())
        // The fuel figure is arithmetic, not receipts — the qualifier is the honesty the
        // legend at the bottom promises.
        if (row.category == SpendCategory.FUEL) {
            append("<span class=\"qualifier\">$SEPARATOR").append(labels.costFuelEstimated.escaped()).append("</span>")
        }
        append("</td>")
        append("<td class=\"c-num\">").append(row.spend.formatRupees().escaped()).append("</td>")
        append("<td class=\"c-num\">").append((row.perKm?.formatRupeesDecimal() ?: EMPTY_FIELD).escaped()).append("</td>")
        append("<td class=\"c-num\">").append(share.toString().escaped()).append("%</td>")
        append("</tr>")
    }

    /* ------------------------- the score's breakdown ------------------------- */

    private fun StringBuilder.appendHealth(details: CarDetails, labels: CarDetailsLabels) {
        // A car nobody has scored has no breakdown to show — same rule as the headline.
        if (details.factors.isEmpty()) return
        val total = details.record.healthScore ?: return

        append("<div class=\"section\">")
        append("<div class=\"block-label\">")
        append(labels.health.escaped()).append(SEPARATOR)
        append(labels.healthPoints(total, FULL_SCORE).escaped())
        append("</div>")
        append("<table class=\"factors\">")
        details.factors.forEach { factor -> appendFactor(factor, labels) }
        append("</table></div>")
    }

    private fun StringBuilder.appendFactor(factor: HealthFactor, labels: CarDetailsLabels) {
        val percent = factor.earned * 100 / factor.max

        append("<tr>")
        append("<td class=\"f-name\">").append(labels.factorName(factor.kind).escaped()).append("</td>")
        append("<td class=\"f-bar\"><div class=\"track\"><div class=\"fill\" style=\"width:$percent%\"></div></div></td>")
        append("<td class=\"f-points\">").append(labels.healthPoints(factor.earned, factor.max).escaped()).append("</td>")
        append("</tr>")
    }

    /* ------------------------- service summary ------------------------- */

    private fun StringBuilder.appendServiceSummary(record: ServiceRecord, labels: CarDetailsLabels) {
        val services = record.rows.filter { it.event is ActivityEvent.Service }
        // A car with no services has nothing to summarise — the export sheet's counts
        // already said zero, and the full record export is the place that prints an
        // empty-state line.
        if (services.isEmpty()) return

        val verified = services.count { it.status == RecordStatus.VERIFIED }
        // Newest first is the record's own order, so the first service row is the latest.
        val last = services.first()

        append("<div class=\"section\">")
        append("<div class=\"block-label\">").append(labels.serviceSummary.escaped()).append("</div>")
        append("<table class=\"cols\"><tr>")

        append("<td class=\"col\"><table class=\"kv\">")
        appendPair(labels.entries, labels.entriesValue(services.size, verified, services.size - verified))
        appendPair(
            key = labels.lastService,
            value = listOfNotNull(
                formatDate(last.date),
                last.odometer?.format(DistanceUnit.KILOMETRE),
            ).joinToString(SEPARATOR),
        )
        append("</table></td>")

        append("<td class=\"col\"><table class=\"kv\">")
        appendPair(labels.fullHistory, labels.fullHistoryValue)
        append("</table></td>")

        append("</tr></table></div>")
    }

    /* ------------------------- the legend ------------------------- */

    private fun StringBuilder.appendHowToRead(labels: CarDetailsLabels) {
        append("<div class=\"howto\">")
        append("<div class=\"block-label\">").append(labels.howToRead.escaped()).append("</div>")
        append("<table class=\"kv\">")
        appendLegend(labels.howToReadRecorded, labels.howToReadRecordedBody)
        appendLegend(labels.howToReadEstimated, labels.howToReadEstimatedBody)
        appendLegend(labels.howToReadScore, labels.howToReadScoreBody)
        append("</table></div>")
    }

    private fun StringBuilder.appendLegend(term: String, explanation: String) {
        append("<tr><td class=\"k strong\">").append(term.escaped()).append("</td>")
        append("<td class=\"v legend\">").append(explanation.escaped()).append("</td></tr>")
    }

    /* ------------------------- helpers ------------------------- */

    /** How old the car is on the day of printing, in whole model years. */
    private fun ServiceRecord.age(): Int? =
        modelYear?.let { year -> (issuedOn.year - year).coerceAtLeast(0) }

    /** "54,120" — the number alone, where the unit is its own smaller suffix. */
    private fun Distance.readableNumber(): String =
        format(DistanceUnit.KILOMETRE).removeSuffix(" $KM_SUFFIX")

    private val KM_SUFFIX = DistanceUnit.KILOMETRE.suffix()

    private const val SEPARATOR = " · "
    private const val EMPTY_FIELD = "—"
    private const val FULL_SCORE = 100

    /** Reserved so a nickname of `<script>` cannot rewrite the document around it. */
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

        .section { margin-top: 5mm; page-break-inside: avoid; break-inside: avoid; }
        .block-label {
          text-transform: uppercase; letter-spacing: 0.11em;
          font-size: 6pt; font-weight: 700; color: $DIM; margin-bottom: 1.6mm;
        }
        .cols { table-layout: fixed; }
        .col { width: 50%; padding-right: 6mm; }
        .kv td { padding: 0.9mm 0; font-size: 8pt; }
        .kv .k { color: $DIM; width: 34%; }
        .kv .v { font-weight: 700; }
        .kv .legend { font-weight: 400; color: $INK; line-height: 1.4; }
        .strong { color: $INK; font-weight: 700; }

        .lined { margin-top: 1mm; }
        .lined th {
          text-transform: uppercase; letter-spacing: 0.1em;
          font-size: 5.8pt; font-weight: 700; color: $DIM;
          text-align: left; padding-bottom: 1.6mm; border-bottom: 0.4pt solid $LINE;
        }
        .lined td { padding: 2.4mm 0; border-bottom: 0.4pt solid $LINE; font-size: 8pt; }
        .lined tr { page-break-inside: avoid; break-inside: avoid; }
        .c-doc { width: 24%; padding-right: 2mm; }
        .c-detail { width: 42%; padding-right: 2mm; color: $DIM; }
        .c-till { width: 18%; padding-right: 2mm; }
        .c-state { width: 16%; text-align: right; }
        th.c-state { text-align: right; }
        .c-cat { width: 46%; padding-right: 2mm; }
        .c-num { width: 18%; text-align: right; }
        th.c-num { text-align: right; }
        .qualifier { color: $DIM; font-weight: 400; font-size: 7.5pt; }
        .ok { color: $GREEN; font-weight: 700; }
        .bad { color: $RED; font-weight: 700; }

        .total {
          display: flex; justify-content: space-between;
          font-weight: 700; font-size: 9.5pt; padding-top: 3mm;
        }
        .total-amount { font-size: 11pt; }

        .factors td { padding: 1.2mm 0; font-size: 8pt; }
        .f-name { width: 30%; color: $DIM; }
        .f-bar { width: 54%; padding-right: 4mm; vertical-align: middle; }
        .f-points { width: 16%; text-align: right; font-weight: 700; }
        .track { background: $LINE; border-radius: 1mm; height: 1.6mm; }
        .fill { background: $INK; border-radius: 1mm; height: 1.6mm; }

        .howto {
          margin-top: 6mm; padding: 4mm; border: 0.4pt solid $LINE; border-radius: 2mm;
          page-break-inside: avoid; break-inside: avoid;
        }
        .howto .k { width: 18%; }
        .disclaimer { color: $MUTED; font-size: 6.8pt; line-height: 1.4; margin-top: 4mm; }
        """.trimIndent()

    // The light palette, hardcoded — paper has one appearance, and a dark-mode PDF is a
    // printer full of toner.
    private const val INK = "#1F1813"
    private const val DIM = "#6F6457"
    private const val MUTED = "#A99D8C"
    private const val LINE = "#E6DCCF"
    private const val GREEN = "#0F9D63"
    private const val RED = "#C2402A"
}
