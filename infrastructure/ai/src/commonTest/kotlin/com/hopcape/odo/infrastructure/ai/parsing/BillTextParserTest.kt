package com.hopcape.odo.infrastructure.ai.parsing

import com.hopcape.odo.core.domain.scan.model.BillType
import com.hopcape.odo.core.domain.scan.model.ExtractionConfidence
import com.hopcape.odo.core.domain.scan.model.ScanId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillTextParserTest {

    private val parser = BillTextParser()
    private val scanId = ScanId("scan-1")

    @Test
    fun `a clean thermal bill parses items total date odometer and workshop`() {
        val bill = parser.parse(
            scanId,
            listOf(
                "SHARMA MOTORS",
                "TAX INVOICE",
                "GSTIN: 27AAPFU0939F1ZV",
                "Date: 14/07/2026",
                "KM Reading: 43,210",
                "Engine Oil 5W40   ₹1,200.00",
                "Oil Filter   350",
                "Labour Charges   400.50",
                "Grand Total   1950.50",
                "Ph: 9876543210",
            ),
        )

        assertEquals("SHARMA MOTORS", bill.workshopName)
        assertEquals(LocalDate(2026, 7, 14), bill.serviceDate)
        assertEquals(43210, bill.odometerKm)
        assertEquals(3, bill.lineItems.size)
        assertEquals("Engine Oil 5W40", bill.lineItems[0].label)
        assertEquals(120000L, bill.lineItems[0].amount.paise)
        assertEquals(35000L, bill.lineItems[1].amount.paise)
        assertEquals(40050L, bill.lineItems[2].amount.paise)
        assertEquals(195050L, bill.total?.paise)
    }

    @Test
    fun `every on-device read is unknown type and requires manual review`() {
        val bill = parser.parse(scanId, listOf("Oil Change  500", "Total  500"))

        assertEquals(BillType.UNKNOWN, bill.billType)
        assertTrue(bill.requiresManualReview)
    }

    @Test
    fun `confidence never reaches the trustworthy threshold`() {
        val everything = parser.parse(
            scanId,
            listOf(
                "SHARMA MOTORS",
                "Date: 14/07/2026",
                "Odometer: 43210",
                "Oil Change   500.00",
                "Total   500.00",
            ),
        )

        assertTrue(everything.confidence.percent < ExtractionConfidence.HIGH)
        assertTrue(!everything.confidence.needsManualReview)
    }

    @Test
    fun `a phone number is never money`() {
        val bill = parser.parse(scanId, listOf("Call us 9876543210"))

        assertTrue(bill.lineItems.isEmpty())
        assertNull(bill.total)
    }

    @Test
    fun `bare integers read as money only within plausible bounds`() {
        val bill = parser.parse(
            scanId,
            listOf(
                "Wheel Alignment 5",   // below the floor: not money
                "Coolant Top-up 250",  // plain amount: money
            ),
        )

        assertTrue(bill.lineItems.any { it.label == "Coolant Top-up" && it.amount.paise == 25000L })
        assertTrue(bill.lineItems.none { it.label == "Wheel Alignment" })
    }

    @Test
    fun `the total line is not also an item`() {
        val bill = parser.parse(scanId, listOf("Oil Change  500", "Amount Payable  590"))

        assertEquals(1, bill.lineItems.size)
        assertEquals(59000L, bill.total?.paise)
    }

    @Test
    fun `two-digit years and dotted dates parse`() {
        assertEquals(
            LocalDate(2026, 1, 5),
            parser.parse(scanId, listOf("Dt. 05.01.26")).serviceDate,
        )
    }

    @Test
    fun `nothing readable is an empty result`() {
        val bill = parser.parse(scanId, listOf("", "   ", "~~~"))

        assertTrue(bill.isEmpty)
        assertEquals(0, bill.confidence.percent)
    }

    @Test
    fun `an odometer needs its hint word and plausible bounds`() {
        assertNull(parser.parse(scanId, listOf("Invoice No 43210")).odometerKm)
        assertNull(parser.parse(scanId, listOf("KM: 9,876,543")).odometerKm)
        assertEquals(43210, parser.parse(scanId, listOf("Odo 43210 km")).odometerKm)
    }

    @Test
    fun `a rupee mark allows amounts outside the bare range`() {
        val bill = parser.parse(scanId, listOf("Advance Paid  ₹5"))

        assertEquals(500L, bill.lineItems.single().amount.paise)
    }

    @Test
    fun `an OCR-dropped slash still reads as money`() {
        val bill = parser.parse(scanId, listOf("5. LABOUR CHARGE 1 500-"))

        val item = bill.lineItems.single()
        assertEquals("LABOUR CHARGE", item.label)
        assertEquals(50000L, item.amount.paise)
        assertTrue(!item.needsCheck)
    }

    @Test
    fun `digit lookalikes in the amount are repaired and flagged for the owner`() {
        val bill = parser.parse(scanId, listOf("2. OlL FILTER 1 200/- Q0o/-"))

        val item = bill.lineItems.single()
        assertEquals(20000L, item.amount.paise)
        assertTrue(item.needsCheck)
    }

    @Test
    fun `a mangled rate is dropped from the label like the money it is`() {
        val bill = parser.parse(scanId, listOf("3. FRONT BRAKE PADS 1 Q50o/- 2500/-"))

        val item = bill.lineItems.single()
        assertEquals("FRONT BRAKE PADS", item.label)
        assertEquals(250000L, item.amount.paise)
        assertTrue(!item.needsCheck)
    }

    @Test
    fun `letters alone are never repaired into money`() {
        val bill = parser.parse(scanId, listOf("4. BRAKE OIL"))

        assertTrue(bill.lineItems.isEmpty())
    }

    @Test
    fun `trailing leader dots and table rules do not hide the money`() {
        val bill = parser.parse(scanId, listOf("Oil Change 500/-.", "TOTAL: 4250/- |"))

        assertEquals(50000L, bill.lineItems.single().amount.paise)
        assertEquals(425000L, bill.total?.paise)
    }

    @Test
    fun `a lookalike-ridden handwritten date is repaired`() {
        val bill = parser.parse(scanId, listOf("DATE: O7/O4/2O"))

        assertEquals(LocalDate(2020, 4, 7), bill.serviceDate)
    }

    @Test
    fun `a lookalike-ridden odometer reading is repaired`() {
        assertEquals(43210, parser.parse(scanId, listOf("KM Reading: 43,2lO")).odometerKm)
    }

    @Test
    fun `month-name dates parse in both orders`() {
        assertEquals(
            LocalDate(2023, 10, 26),
            parser.parse(scanId, listOf("Date: October 26, 2023")).serviceDate,
        )
        assertEquals(
            LocalDate(2023, 10, 26),
            parser.parse(scanId, listOf("Dated 26th Oct, 2023")).serviceDate,
        )
    }

    @Test
    fun `a GSTIN tail is never repaired into money`() {
        val bill = parser.parse(scanId, listOf("GSTIN: 27AABCV1234F1Z0"))

        assertTrue(bill.lineItems.isEmpty())
    }

    @Test
    fun `the bill's paperwork never becomes an item`() {
        val bill = parser.parse(
            scanId,
            listOf(
                "Invoice No: 10567 Mobile: +91 88888 77777",
                "Vehicle: Honda City, MH-12-AB-5678 2500",
                "Description Qty Unit Price 2500",
                "CGST (9%) 792.00",
                "SGST (9%) 792.00",
                "Subtotal: 8,800.00",
                "Wheel Balancing 400/-",
            ),
        )

        assertEquals(listOf("Wheel Balancing"), bill.lineItems.map { it.label })
    }

    /** The printed invoice from the field, as its rows read once the tilt is corrected. */
    @Test
    fun `a printed garage invoice parses end to end`() {
        val bill = parser.parse(
            scanId,
            listOf(
                "SAI MOTOR WORKS",
                "123, MG Road, Pune, Maharashtra - 411001",
                "Contact: +91 98765 43210",
                "GSTIN: 27AABCV1234F1Z0",
                "CAR SERVICING INVOICE / BILL",
                "Invoice No: 10567 Date: October 26, 2023",
                "Customer: Mr. Ajay Kumar Mobile: +91 88888 77777",
                "Vehicle: Honda City, MH-12-AB-5678 Odometer: 45,000 KM",
                "S. No. Description Qty Unit Price (₹) Amount (₹)",
                "1 Engine Oil Change (Premium 5W-30) 1 ₹2,500.00 ₹2,500.00",
                "2 Oil Filter (Original Honda) 1 ₹500.00 ₹500.00",
                "3 Front Brake Pad Replacement (Set of 4) 1 ₹3,500.00 ₹3,500.00",
                "4 Labour Charges (Servicing & Repairs) 1 ₹1,800.00 ₹1,800.00",
                "5 General Vehicle Inspection (Fluids, Belts, Lights check) 1 ₹500.00 ₹500.00",
                "Subtotal: ₹8,800.00",
                "CGST (9%): ₹792.00",
                "SGST (9%): ₹792.00",
                "Total Amount (₹): ₹10,384.00",
                "Manager's Signature",
            ),
        )

        assertEquals("SAI MOTOR WORKS", bill.workshopName)
        assertEquals(LocalDate(2023, 10, 26), bill.serviceDate)
        assertEquals(45000, bill.odometerKm)
        assertEquals(
            listOf(250000L, 50000L, 350000L, 180000L, 50000L),
            bill.lineItems.map { it.amount.paise },
        )
        assertEquals("Engine Oil Change (Premium", bill.lineItems.first().label)
        assertEquals(1038400L, bill.total?.paise)
    }

    @Test
    fun `an item's name is capped at four words`() {
        val bill = parser.parse(
            scanId,
            listOf("Engine Oil Fully Synthetic 5W40 Premium Grade  1200/-"),
        )

        assertEquals("Engine Oil Fully Synthetic", bill.lineItems.single().label)
    }

    @Test
    fun `Z and G read as the digits they resemble`() {
        val bill = parser.parse(scanId, listOf("Wiper Blade 1 Z5o/-"))

        val item = bill.lineItems.single()
        assertEquals(25000L, item.amount.paise)
        assertTrue(item.needsCheck)
    }

    /**
     * The failing scan from the field, as the device's OCR actually composed it: browser
     * tabs and UI text above the paper, the shop name cut off at the photo's edge, the
     * handwritten date misread beyond repair, and lookalike digits in the money column.
     */
    @Test
    fun `a real photographed cash memo with screen noise parses`() {
        val bill = parser.parse(
            scanId,
            listOf(
                "Adve x G Dele x4 View X Gets X Logi X G wher",
                "in e",
                "SHREE VINAYAK AUTO GARAGE & SERVICE CE&FO",
                "CASH MEMO/BILL",
                "DATE: 97/94129. VEHICLE NO.: NA12.92. Vehicle No.:.",
                "CUSTOMER NAME: GRAHANMAT SETH MOBILE NO.:",
                "S.NO. DESCRIPTION QTY RATE AMOUNT",
                "1. ENGINE OIL 1000/- 1000/-",
                "2. OlL FILTER 1 200/- Q0o/-",
                "3. FRONT BRAKE PADS 1 2500/- 2500/-",
                "4. BRAKE OIL 1 50/- 50/-",
                "5. LABOUR CHARGE 1 500/-",
                "TOTAL: 4250/-",
                "MECHANIC: Mechanie",
                "Describe your image",
                "Gemini is Al and can make mistakes.",
            ),
        )

        assertEquals("SHREE VINAYAK AUTO GARAGE & SERVICE CE&FO", bill.workshopName)
        assertNull(bill.serviceDate) // 97/94129 is not a date; inventing one would be worse
        assertEquals(
            listOf("ENGINE OIL", "OlL FILTER", "FRONT BRAKE PADS", "BRAKE OIL", "LABOUR CHARGE"),
            bill.lineItems.map { it.label },
        )
        assertEquals(
            listOf(100000L, 20000L, 250000L, 5000L, 50000L),
            bill.lineItems.map { it.amount.paise },
        )
        assertEquals(listOf(false, true, false, false, false), bill.lineItems.map { it.needsCheck })
        assertEquals(425000L, bill.total?.paise)
        assertTrue(bill.requiresManualReview)
    }
}
