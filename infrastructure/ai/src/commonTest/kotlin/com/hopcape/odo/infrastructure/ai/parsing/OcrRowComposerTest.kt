package com.hopcape.odo.infrastructure.ai.parsing

import com.hopcape.odo.core.domain.scan.model.ScanId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrRowComposerTest {

    private val composer = OcrRowComposer()

    @Test
    fun `column-separated cells rejoin into their printed row`() {
        val rows = composer.rows(
            listOf(
                OcrLine("1000/-", left = 500, top = 100, right = 560, bottom = 120),
                OcrLine("ENGINE OIL", left = 60, top = 102, right = 200, bottom = 122),
                OcrLine("1", left = 400, top = 101, right = 410, bottom = 119),
            ),
        )

        assertEquals(listOf("ENGINE OIL 1 1000/-"), rows)
    }

    @Test
    fun `rows stay separate and ordered top to bottom`() {
        val rows = composer.rows(
            listOf(
                OcrLine("OIL FILTER", left = 60, top = 150, right = 180, bottom = 170),
                OcrLine("ENGINE OIL", left = 60, top = 100, right = 180, bottom = 120),
                OcrLine("200/-", left = 500, top = 152, right = 550, bottom = 172),
                OcrLine("1000/-", left = 500, top = 98, right = 550, bottom = 118),
            ),
        )

        assertEquals(listOf("ENGINE OIL 1000/-", "OIL FILTER 200/-"), rows)
    }

    @Test
    fun `handwriting drifting off the ruled line still joins its row`() {
        // The handwritten amount sits a third of a line lower than the printed label.
        val rows = composer.rows(
            listOf(
                OcrLine("TOTAL:", left = 300, top = 100, right = 360, bottom = 120),
                OcrLine("4250/-", left = 500, top = 108, right = 560, bottom = 130),
            ),
        )

        assertEquals(listOf("TOTAL: 4250/-"), rows)
    }

    @Test
    fun `blank runs are dropped`() {
        assertTrue(composer.rows(listOf(OcrLine("  ", 0, 0, 10, 10))).isEmpty())
    }

    /**
     * The failing bill from the field, as ML Kit hands it over: one run per table cell,
     * columns apart. Composition plus parsing must recover the whole memo.
     */
    @Test
    fun `a handwritten cash memo parses end to end`() {
        fun row(y: Int, vararg cells: Pair<Int, String>) =
            cells.map { (x, text) -> OcrLine(text, left = x, top = y, right = x + 80, bottom = y + 22) }

        val runs =
            row(10, 100 to "SHREE VINAYAK AUTO GARAGE & SERVICE CENTER") +
                row(40, 300 to "CASH MEMO / BILL") +
                row(70, 60 to "DATE:", 130 to "07/04/20", 300 to "VEHICLE NO.:", 420 to "NA12-02") +
                row(100, 60 to "CUSTOMER NAME:", 220 to "GRAHANMAT SETH", 430 to "MOBILE NO.:") +
                row(130, 40 to "S.NO.", 120 to "DESCRIPTION", 400 to "QTY", 480 to "RATE", 560 to "AMOUNT") +
                row(160, 40 to "1.", 120 to "ENGINE OIL", 400 to "1", 480 to "1000/-", 560 to "1000/-") +
                row(190, 40 to "2.", 120 to "OIL FILTER", 400 to "1", 480 to "200/-", 560 to "200/-") +
                row(220, 40 to "3.", 120 to "FRONT BRAKE PADS", 400 to "1", 480 to "2500/-", 560 to "2500/-") +
                row(250, 40 to "4.", 120 to "BRAKE OIL", 400 to "1", 480 to "50/-", 560 to "50/-") +
                row(280, 40 to "5.", 120 to "LABOUR CHARGE", 400 to "1", 480 to "—", 560 to "500/-") +
                row(310, 460 to "TOTAL:", 560 to "4250/-") +
                row(340, 440 to "MECHANIC:")

        val bill = BillTextParser().parse(ScanId("scan-1"), composer.rows(runs))

        assertEquals("SHREE VINAYAK AUTO GARAGE & SERVICE CENTER", bill.workshopName)
        assertEquals(LocalDate(2020, 4, 7), bill.serviceDate)
        assertEquals(
            listOf("ENGINE OIL", "OIL FILTER", "FRONT BRAKE PADS", "BRAKE OIL", "LABOUR CHARGE"),
            bill.lineItems.map { it.label },
        )
        assertEquals(
            listOf(100000L, 20000L, 250000L, 5000L, 50000L),
            bill.lineItems.map { it.amount.paise },
        )
        assertEquals(425000L, bill.total?.paise)
        assertTrue(bill.requiresManualReview)
    }
}
