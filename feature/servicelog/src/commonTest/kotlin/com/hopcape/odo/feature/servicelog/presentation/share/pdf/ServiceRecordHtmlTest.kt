package com.hopcape.odo.feature.servicelog.presentation.share.pdf

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.record.analysis.ServiceRecordBuilder
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.feature.servicelog.presentation.testCar
import com.hopcape.odo.feature.servicelog.presentation.testDocument
import com.hopcape.odo.feature.servicelog.presentation.testEntry
import com.hopcape.odo.feature.servicelog.presentation.testOwner
import com.hopcape.odo.feature.servicelog.presentation.testSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The printed document, asserted as a string.
 *
 * There is no renderer here — that is a device's job and is covered on one. What can be
 * checked without one is everything that decides what the owner is about to send: that each
 * entry reaches the page, that the figures agree with the record, that nothing the app
 * cannot back gets printed, and that a car with an angle bracket in its name cannot rewrite
 * the document around it.
 */
class ServiceRecordHtmlTest {

    private val today = LocalDate(2026, 8, 12)

    /**
     * Plain English stand-ins for the resource lookups. The point of the labels object is
     * that the template never reads a resource, which is exactly what lets this run on the
     * host JVM.
     */
    private val labels = ServiceRecordLabels(
        headerPrefix = "Service record",
        issued = { date -> "Issued $date" },
        eyebrow = "Verified service record",
        statHealth = "Health score",
        statHealthUnit = "/100",
        statEntries = "Entries",
        statVerified = "Verified",
        statVerifiedOf = { total -> "of $total" },
        statSince = "Record since",
        ownership = "Ownership",
        owner = "Owner",
        ownedSince = "Owned since",
        odometer = "Odometer",
        odometerConsistent = "Consistent across all entries",
        odometerInconsistent = "A reading falls below an earlier one",
        documents = "Documents on file",
        documentName = { type -> type.name.lowercase().replaceFirstChar(Char::uppercase) },
        validTill = { date -> "Valid till $date" },
        onFile = "On file",
        expired = { date -> "Expired $date" },
        timeline = "Service timeline",
        columnDate = "Date",
        columnOdometer = "Odo",
        columnWork = "Work done",
        columnStatus = "Status",
        columnAmount = "Amount",
        statusVerified = "Verified",
        statusSelfReported = "Self-reported",
        documentAdded = { name -> "$name added" },
        documentRenewed = { name -> "$name renewed" },
        carAdded = "Added to Odo",
        workUnspecified = "Service",
        categoryName = { category -> category.name },
        fuelName = { fuel -> fuel.name.lowercase().replaceFirstChar(Char::uppercase) },
        total = "Total on record",
        howToRead = "How to read this record",
        howToReadVerified = "A bill image was uploaded.",
        howToReadSelfReported = "Entered without a bill.",
        howToReadOdometer = "Readings only move forward.",
        disclaimer = "Not an inspection report.",
        footer = "Verified entries are backed by a bill image.",
        empty = "Nothing has been logged for this car yet.",
        documentTitle = { car -> "$car service record" },
    )

    private val fonts = ServiceRecordHtml.Fonts(regularBase64 = "AAAA", boldBase64 = "BBBB")

    private fun record(
        car: Car? = testCar(),
        owner: OwnerProfile? = testOwner(),
        entries: List<ServiceLogEntry> = emptyList(),
        documents: List<Document> = emptyList(),
        scores: List<HealthSnapshot> = emptyList(),
    ): ServiceRecord = ServiceRecordBuilder.build(
        car = car,
        owner = owner,
        entries = entries,
        documents = documents,
        scores = scores,
        today = today,
        zone = TimeZone.UTC,
    )

    private fun html(record: ServiceRecord): String = ServiceRecordHtml.build(record, labels, fonts)

    /**
     * kotlin.test's own `assertContains` takes `ignoreCase` third, not a message, so this
     * exists to say *why* a line belongs on the page when the assertion fails.
     */
    private fun assertPrinted(page: String, expected: String, message: String? = null) =
        assertTrue(page.contains(expected), message ?: "the document does not contain: $expected")

    /* ------------------------- the entries ------------------------- */

    @Test
    fun `every entry reaches the page`() {
        val page = html(
            record(
                entries = listOf(
                    testEntry("a", km = 54_000, paise = 320_000, verified = true, date = LocalDate(2026, 7, 12), notes = "Oil change"),
                    testEntry("b", km = 51_400, paise = 240_000, verified = false, date = LocalDate(2026, 6, 4), notes = "AC re-gas"),
                ),
            ),
        )

        assertPrinted(page, "Oil change")
        assertPrinted(page, "AC re-gas")
        assertPrinted(page, "Rs. 3,200")
        assertPrinted(page, "Rs. 2,400")
        assertPrinted(page, "12 Jul 2026")
        assertPrinted(page, "Sharma Motors", "the workshop is what makes an entry checkable")
    }

    @Test
    fun `a hundred entries all reach the page`() {
        val entries = (1..100).map { index ->
            testEntry("log-$index", km = 10_000 + index * 100, paise = 100_000, date = LocalDate(2026, 1, 1), notes = "Job $index")
        }
        val page = html(record(entries = entries))

        val printed = (1..100).count { page.contains(">Job $it<") }
        assertEquals(100, printed, "a long history must not be silently cut short")
    }

    @Test
    fun `the total and the counts agree with the record`() {
        val built = record(
            entries = listOf(
                testEntry("a", km = 54_000, paise = 320_000, verified = true, date = LocalDate(2026, 7, 12)),
                testEntry("b", km = 51_400, paise = 240_000, verified = false, date = LocalDate(2026, 6, 4)),
            ),
        )
        val page = html(built)

        assertPrinted(page, "Rs. 5,600", "the printed total")
        assertPrinted(page, "of ${built.entryCount}", "verified is stated out of the entry count")
    }

    @Test
    fun `a service with no amount and no reading prints a dash rather than a zero`() {
        val page = html(record(documents = listOf(testDocument(DocumentType.INSURANCE))))

        assertPrinted(page, "Insurance added")
        assertPrinted(page, "—", "a document row has no odometer and no amount to print")
    }

    /* ------------------------- what must never be printed ------------------------- */

    @Test
    fun `the document carries no image and no verify link`() {
        val page = html(record(entries = listOf(testEntry("a", km = 1, date = today))))

        assertFalse(page.contains("<img"), "the QR block is out until there is a page to scan to")
        assertFalse(page.contains("odo.app/v/"), "a link that resolves to nothing must not be printed")
        assertFalse(page.contains("<script"), "the document is shared offline and runs nothing")
    }

    @Test
    fun `nothing claims an accident history`() {
        val page = html(record()).lowercase()

        assertFalse(
            page.contains("accident"),
            "the app never asked, so the document must not answer on the owner's behalf",
        )
    }

    @Test
    fun `a car with no services makes no claim about its odometer`() {
        val page = html(record())

        assertFalse(
            page.contains("Consistent across all entries"),
            "there are no entries to be consistent across",
        )
    }

    @Test
    fun `a car with services does state whether the odometer holds up`() {
        val consistent = html(
            record(
                entries = listOf(
                    testEntry("a", km = 31_400, date = LocalDate(2025, 2, 11)),
                    testEntry("b", km = 54_000, date = LocalDate(2026, 7, 12)),
                ),
            ),
        )
        val rolledBack = html(
            record(
                entries = listOf(
                    testEntry("a", km = 54_000, date = LocalDate(2025, 2, 11)),
                    testEntry("b", km = 31_400, date = LocalDate(2026, 7, 12)),
                ),
            ),
        )

        assertPrinted(consistent, "Consistent across all entries")
        assertPrinted(rolledBack, "A reading falls below an earlier one")
    }

    /* ------------------------- documents block ------------------------- */

    @Test
    fun `an empty vault drops the documents heading rather than printing it empty`() {
        val page = html(record())

        assertFalse(page.contains("Documents on file"), "a heading over nothing is noise")
    }

    @Test
    fun `an expired paper is listed without a tick`() {
        val expired = html(
            record(documents = listOf(testDocument(DocumentType.PUC, expiresOn = LocalDate(2026, 1, 1)))),
        )
        val valid = html(
            record(documents = listOf(testDocument(DocumentType.PUC, expiresOn = LocalDate(2027, 1, 1)))),
        )

        assertPrinted(expired, "Expired 1 Jan 2026")
        assertFalse(expired.contains("✓"), "a tick beside an expired policy would read as cover")
        assertTrue(valid.contains("✓"), "a policy in force is ticked")
    }

    /* ------------------------- the masthead ------------------------- */

    @Test
    fun `the masthead names the car, the plate and the reading`() {
        val page = html(record(scores = listOf(testSnapshot("2026-07-02T10:00:00Z", total = 74))))

        assertPrinted(page, "Maruti Swift VXI")
        assertPrinted(page, "MH 12 AB 1234", "the plate is grouped the way a plate is read")
        assertPrinted(page, "54,120 km")
        assertPrinted(page, "Petrol")
        assertPrinted(page, ">74<", "the health score")
        assertPrinted(page, "Issued 12 Aug 2026")
    }

    @Test
    fun `a car nobody has scored prints no score`() {
        val page = html(record())

        assertFalse(page.contains("/100"), "the unit belongs to a figure that isn't there")
    }

    /* ------------------------- safety and shape ------------------------- */

    @Test
    fun `a car name carrying markup cannot rewrite the document`() {
        val page = html(record(car = testCar(nickname = "<script>alert(1)</script>")))

        assertFalse(page.contains("<script>alert"), "the name must arrive as text, not as markup")
        assertPrinted(page, "&lt;script&gt;alert(1)&lt;/script&gt;")
    }

    @Test
    fun `the document is self-contained and set in the brand face`() {
        val page = html(record())

        assertPrinted(page, "@font-face")
        assertPrinted(page, "data:font/ttf;base64,AAAA", "the regular weight is embedded")
        assertPrinted(page, "data:font/ttf;base64,BBBB", "the bold weight is embedded")
        assertFalse(page.contains("http://"), "nothing is fetched")
        assertFalse(page.contains("https://"), "nothing is fetched")
    }

    @Test
    fun `the running header and footer repeat as table sections`() {
        val page = html(record())

        assertPrinted(page, "<thead>", "a thead is what both print engines repeat per page")
        assertPrinted(page, "<tfoot>")
        assertPrinted(page, "Verified entries are backed by a bill image.")
    }

    @Test
    fun `an empty record says so instead of printing an empty table`() {
        val page = html(record(car = null, owner = null))

        assertPrinted(page, "Nothing has been logged for this car yet.")
        assertFalse(page.contains("<tbody><tr><td class=\"c-date\""), "no rows means no row markup")
    }

    @Test
    fun `the page is A4 with its own margins`() {
        val page = html(record())

        assertPrinted(page, "@page { size: A4;")
        assertPrinted(page, "page-break-inside: avoid", "a row must not be cut in half by a page break")
    }

    @Test
    fun `the fuel type and validity of a document both reach the page`() {
        val built = record(
            car = testCar(),
            documents = listOf(testDocument(DocumentType.INSURANCE, expiresOn = LocalDate(2027, 7, 3))),
        )
        val page = html(built)

        assertEquals(FuelType.PETROL, built.fuelType)
        assertEquals(1, built.documents.size)
        assertTrue(built.documents.single().validity is DocumentValidity.Valid)
        assertPrinted(page, "Valid till 3 Jul 2027")
    }
}
