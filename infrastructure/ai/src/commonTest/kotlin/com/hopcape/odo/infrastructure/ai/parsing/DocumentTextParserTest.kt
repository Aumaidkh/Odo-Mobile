package com.hopcape.odo.infrastructure.ai.parsing

import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.core.domain.scan.model.ExtractionConfidence
import com.hopcape.odo.core.domain.scan.model.ScanId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules that turn OCR text off a vehicle paper into a filled-in review screen.
 *
 * Every fixture is written the way the papers are actually printed — the phrasing on an
 * Indian insurance certificate, PUC slip, RC and driving licence — because that is what the
 * parser meets, not tidy key-value pairs.
 */
class DocumentTextParserTest {

    private val parser = DocumentTextParser()

    private fun parse(vararg lines: String): ExtractedDocument =
        parser.parse(ScanId("scan-1"), lines.toList())

    /* ------------------------------ What kind of paper ------------------------------ */

    @Test
    fun `an insurance certificate is recognised with its cover dates`() {
        val result = parse(
            "SafeDrive General Insurance Co. Ltd",
            "CERTIFICATE OF INSURANCE CUM POLICY SCHEDULE",
            "Policy No: 3001/12345678/00/000",
            "Period of Insurance: Valid From 15/03/2026 To 14/03/2027",
            "Insured Declared Value (IDV): Rs. 4,50,000",
        )

        assertEquals(DocumentType.INSURANCE, result.documentType)
        assertEquals(LocalDate(2026, 3, 15), result.issuedOn)
        assertEquals(LocalDate(2027, 3, 14), result.expiresOn)
    }

    @Test
    fun `a puc certificate is recognised`() {
        val result = parse(
            "POLLUTION UNDER CONTROL CERTIFICATE",
            "Regn No: MH12AB1234",
            "Carbon Monoxide: 0.21%",
            "Valid Upto: 09/11/2026",
        )

        assertEquals(DocumentType.PUC, result.documentType)
        assertEquals(LocalDate(2026, 11, 9), result.expiresOn)
    }

    @Test
    fun `a registration certificate is recognised`() {
        val result = parse(
            "CERTIFICATE OF REGISTRATION",
            "Chassis No: MA3EYD1S1PB123456",
            "Engine No: K12MN1234567",
            "Fuel Used: PETROL",
            "Cubic Capacity: 1197",
            "Valid Till: 31/12/2035",
        )

        assertEquals(DocumentType.RC, result.documentType)
        assertEquals(LocalDate(2035, 12, 31), result.expiresOn)
    }

    @Test
    fun `a driving licence is recognised`() {
        val result = parse(
            "DRIVING LICENCE",
            "DL No: MH02 20110012345",
            "Date of Birth: 04/08/1990",
            "Blood Group: O+",
            "Valid Till: 03/08/2040",
        )

        assertEquals(DocumentType.LICENCE, result.documentType)
        assertEquals(LocalDate(2040, 8, 3), result.expiresOn)
    }

    /**
     * These papers quote each other — an RC names the insurance, a PUC names the
     * registration — so the type is whichever vocabulary appears most, not whichever
     * appears first.
     */
    @Test
    fun `a paper that mentions another kind is still read as itself`() {
        val result = parse(
            "POLLUTION UNDER CONTROL CERTIFICATE",
            "Emission test as per Motor Vehicles Act",
            "Registration Certificate No: MH12AB1234",
            "Smoke Density: 0.42",
            "Valid Upto: 09/11/2026",
        )

        assertEquals(DocumentType.PUC, result.documentType)
    }

    @Test
    fun `a paper naming no kind at all leaves the type to the owner`() {
        val result = parse("Some scanned page", "Nothing useful here")

        assertNull(result.documentType)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `an even split between two kinds is not guessed at`() {
        // One word each: naming a winner here would be a coin toss wearing a verdict's clothes.
        val result = parse("Insurance", "Pollution", "Nothing else on the page")

        assertNull(result.documentType)
    }

    /* ------------------------------ The dates ------------------------------ */

    @Test
    fun `a labelled expiry wins over every other date on the paper`() {
        val result = parse(
            "CERTIFICATE OF INSURANCE",
            "Date of Issue: 01/04/2026",
            "Printed on 02/04/2026",
            "Valid Till: 31/03/2027",
        )

        assertEquals(LocalDate(2027, 3, 31), result.expiresOn)
        assertEquals(LocalDate(2026, 4, 1), result.issuedOn)
    }

    @Test
    fun `an unlabelled pair of dates reads as a cover period`() {
        val result = parse("PUC", "09/11/2025 09/11/2026")

        assertEquals(LocalDate(2025, 11, 9), result.issuedOn)
        assertEquals(LocalDate(2026, 11, 9), result.expiresOn)
    }

    /**
     * A single unlabelled date is as likely to be a print date as an expiry, and a wrong
     * expiry is a reminder that fires on the wrong day.
     */
    @Test
    fun `a single unlabelled date is not turned into an expiry`() {
        val result = parse("CERTIFICATE OF INSURANCE", "Printed 02/04/2026")

        assertNull(result.expiresOn)
        assertNull(result.issuedOn)
    }

    @Test
    fun `a spelled out expiry parses`() {
        val result = parse("CERTIFICATE OF INSURANCE", "Valid till 14 March 2027")

        assertEquals(LocalDate(2027, 3, 14), result.expiresOn)
    }

    @Test
    fun `an expiry with OCR digit lookalikes is repaired`() {
        // `O9/II/2O26` — a handwritten-looking slip read by ML Kit.
        val result = parse("POLLUTION UNDER CONTROL", "Valid Upto: O9/11/2O26")

        assertEquals(LocalDate(2026, 11, 9), result.expiresOn)
    }

    @Test
    fun `a two digit year reads as this century`() {
        val result = parse("CERTIFICATE OF INSURANCE", "Valid Till: 14/03/27")

        assertEquals(LocalDate(2027, 3, 14), result.expiresOn)
    }

    /* ------------------------------ The label ------------------------------ */

    @Test
    fun `the insurer printed above the heading becomes the suggested title`() {
        val result = parse(
            "SafeDrive General Insurance Co. Ltd",
            "CERTIFICATE OF INSURANCE",
            "Valid Till: 31/03/2027",
        )

        assertEquals("SafeDrive General Insurance Co. Ltd", result.suggestedTitle)
    }

    @Test
    fun `a policy number is never offered as a title`() {
        val result = parse(
            "Policy No 3001123456",
            "CERTIFICATE OF INSURANCE",
            "Valid Till: 31/03/2027",
        )

        assertNull(result.suggestedTitle)
    }

    /** Every other kind is already fully described by its type, so a name adds nothing. */
    @Test
    fun `only an insurance policy is given a title`() {
        val result = parse(
            "Regional Transport Office",
            "POLLUTION UNDER CONTROL CERTIFICATE",
            "Valid Upto: 09/11/2026",
        )

        assertNull(result.suggestedTitle)
    }

    /* ------------------------------ Confidence ------------------------------ */

    @Test
    fun `a read with no expiry always asks to be checked`() {
        val result = parse("CERTIFICATE OF INSURANCE", "Policy No: 3001/1234")

        assertTrue(result.requiresManualReview, "no expiry must never be trusted")
    }

    @Test
    fun `confidence never reaches the trustworthy threshold`() {
        // Everything found: type, both dates, a title. Still short of HIGH by design.
        val result = parse(
            "SafeDrive General Insurance Co. Ltd",
            "CERTIFICATE OF INSURANCE",
            "Valid From 15/03/2026 To 14/03/2027",
        )

        assertTrue(
            result.confidence.percent < ExtractionConfidence.HIGH,
            "an on-device read is never trustworthy: ${result.confidence.percent}",
        )
    }
}
