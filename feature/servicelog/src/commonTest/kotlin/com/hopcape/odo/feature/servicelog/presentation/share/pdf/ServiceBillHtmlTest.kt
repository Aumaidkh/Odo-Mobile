package com.hopcape.odo.feature.servicelog.presentation.share.pdf

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.record.analysis.ServiceRecordBuilder
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItem
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.servicelog.presentation.testCar
import com.hopcape.odo.feature.servicelog.presentation.testEntry
import com.hopcape.odo.feature.servicelog.presentation.testOwner
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The printed bill, asserted as a string — the single-entry counterpart of
 * [ServiceRecordHtmlTest], checked the same way: no renderer, just everything that decides
 * what the owner is about to send. Each item and its price reach the page, the figures are
 * the entry's own, nothing from the rest of the record leaks in, and markup in a label
 * cannot rewrite the document.
 */
class ServiceBillHtmlTest {

    private val today = LocalDate(2026, 8, 12)

    /** Plain English stand-ins for the resource lookups, as in the record's test. */
    private val labels = ServiceBillLabels(
        headerPrefix = "Service bill",
        issued = { date -> "Issued $date" },
        eyebrow = "Verified service bill",
        eyebrowSelfReported = "Service bill",
        statDate = "Service date",
        statOdometer = "Odometer",
        statStatus = "Status",
        statTotal = "Bill total",
        statusVerified = "Verified",
        statusSelfReported = "Self-reported",
        workshop = "Workshop",
        notes = "Notes",
        items = "Billed items",
        columnItem = "Item",
        columnCategory = "Category",
        columnAmount = "Amount",
        workUnspecified = "Service",
        categoryName = { category -> category.name },
        fuelName = { fuel -> fuel.name.lowercase().replaceFirstChar(Char::uppercase) },
        total = "Bill total",
        disclaimer = "Not an inspection report.",
        footer = "A verified bill is backed by the bill image.",
        documentTitle = { car -> "$car service bill" },
    )

    private val fonts = ServiceRecordHtml.Fonts(regularBase64 = "AAAA", boldBase64 = "BBBB")

    private fun rupees(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }

    /** The car's record, built around [entry] — the bill only reads the car identity off it. */
    private fun record(entry: ServiceLogEntry): ServiceRecord = ServiceRecordBuilder.build(
        car = testCar(),
        owner = testOwner(),
        entries = listOf(entry),
        documents = emptyList(),
        scores = emptyList(),
        today = today,
        zone = TimeZone.UTC,
    )

    private fun html(entry: ServiceLogEntry): String =
        ServiceBillHtml.build(record(entry), entry, labels, fonts)

    private fun assertPrinted(page: String, expected: String, message: String? = null) =
        assertTrue(page.contains(expected), message ?: "the document does not contain: $expected")

    private val scannedBill = testEntry(
        "bill-entry",
        km = 54_000,
        paise = 320_000,
        verified = true,
        date = LocalDate(2026, 7, 12),
        workshop = "Sharma Motors",
        pricedItems = listOf(
            ServiceLogLineItem("Premium Synthetic Engine Oil", ServiceCategory.OIL_CHANGE, rupees(190_000)),
            ServiceLogLineItem("Genuine Oil Filter", ServiceCategory.OIL_CHANGE, rupees(45_000)),
            ServiceLogLineItem("AC re-gas", ServiceCategory.AC, rupees(85_000)),
        ),
    )

    /* ------------------------- the items ------------------------- */

    @Test
    fun `every billed item reaches the page with its own price`() {
        val page = html(scannedBill)

        assertPrinted(page, "Premium Synthetic Engine Oil")
        assertPrinted(page, "Rs. 1,900")
        assertPrinted(page, "Genuine Oil Filter")
        assertPrinted(page, "Rs. 450")
        assertPrinted(page, "AC re-gas")
        assertPrinted(page, "Rs. 850")
    }

    @Test
    fun `the bill prints its own figures not the record's`() {
        val page = html(scannedBill)

        assertPrinted(page, "12 Jul 2026")
        assertPrinted(page, "54,000 km", "the reading the bill was raised at")
        assertPrinted(page, "Rs. 3,200", "the entry's own total")
        assertPrinted(page, "Sharma Motors", "the workshop is what makes a bill checkable")
        assertPrinted(page, "Verified service bill")
    }

    @Test
    fun `nothing from the rest of the record leaks onto the bill`() {
        val page = html(scannedBill)

        assertFalse(page.contains("Health score"), "the record's stats belong to the record")
        assertFalse(page.contains("Ownership"), "no ownership block on a bill")
        assertFalse(page.contains("Added to Odo"), "no timeline milestones on a bill")
    }

    @Test
    fun `an unpriced entry still says what it was for`() {
        val page = html(
            testEntry(
                "tagged",
                km = 48_500,
                paise = 480_000,
                date = LocalDate(2026, 3, 2),
                categories = setOf(ServiceCategory.BRAKES),
            ),
        )

        assertPrinted(page, "BRAKES", "the category stands in for the missing lines")
        assertPrinted(page, "—", "a line without its own price prints a dash, not the total")
        assertPrinted(page, "Rs. 4,800", "the total is still the entry's own figure")
    }

    @Test
    fun `a line entered without a label falls back to its category`() {
        val page = html(
            testEntry(
                "unlabelled",
                km = 46_100,
                paise = 150_000,
                date = LocalDate(2026, 1, 14),
                pricedItems = listOf(ServiceLogLineItem(null, ServiceCategory.TYRES, rupees(150_000))),
            ),
        )

        assertPrinted(page, "<div class=\"work\">TYRES</div>", "no row prints blank")
    }

    /* ------------------------- what the page claims ------------------------- */

    @Test
    fun `a self-reported entry is not called verified`() {
        val page = html(
            testEntry("self", km = 43_200, paise = 640_000, verified = false, date = LocalDate(2025, 12, 18)),
        )

        assertPrinted(page, "Service bill")
        assertFalse(page.contains("Verified service bill"), "the eyebrow must not promote the owner's word")
        assertPrinted(page, "Self-reported")
    }

    @Test
    fun `the masthead names the car and the running header repeats`() {
        val page = html(scannedBill)

        assertPrinted(page, "Maruti Swift VXI")
        assertPrinted(page, "MH 12 AB 1234", "the plate is grouped the way a plate is read")
        assertPrinted(page, "Issued 12 Aug 2026")
        assertPrinted(page, "<thead>", "a thead is what both print engines repeat per page")
        assertPrinted(page, "<tfoot>")
    }

    /* ------------------------- safety and shape ------------------------- */

    @Test
    fun `an item label carrying markup cannot rewrite the document`() {
        val page = html(
            testEntry(
                "hostile",
                km = 1,
                paise = 100,
                date = LocalDate(2026, 1, 1),
                pricedItems = listOf(
                    ServiceLogLineItem("<script>alert(1)</script>", ServiceCategory.OTHER, rupees(100)),
                ),
            ),
        )

        assertFalse(page.contains("<script>alert"), "the label must arrive as text, not as markup")
        assertPrinted(page, "&lt;script&gt;alert(1)&lt;/script&gt;")
    }

    @Test
    fun `the document is self-contained A4 and set in the brand face`() {
        val page = html(scannedBill)

        assertPrinted(page, "@font-face")
        assertPrinted(page, "data:font/ttf;base64,AAAA", "the regular weight is embedded")
        assertPrinted(page, "data:font/ttf;base64,BBBB", "the bold weight is embedded")
        assertFalse(page.contains("http://"), "nothing is fetched")
        assertFalse(page.contains("https://"), "nothing is fetched")
        assertFalse(page.contains("<script"), "the document is shared offline and runs nothing")
        assertPrinted(page, "@page { size: A4;")
    }
}
