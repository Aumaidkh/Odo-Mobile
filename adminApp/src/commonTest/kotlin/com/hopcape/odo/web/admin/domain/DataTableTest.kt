package com.hopcape.odo.web.admin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataTableTest {

    private val table = DataTable(
        name = "part_prices",
        columns = listOf("part_slug", "unit", "mrp", "source_note"),
        rows = listOf(
            listOf("oil-filter", "each", "450", "OEM list, March"),
            listOf("air-filter", "each", "620", ""),
        ),
    )

    @Test
    fun `json round trips exactly`() {
        val back = TableDocument.read(TableDocument.write(table, TableFormat.Json))
        assertEquals(table, back)
    }

    @Test
    fun `csv round trips the rows, without the table name`() {
        val back = TableDocument.read(TableDocument.write(table, TableFormat.Csv))
        assertEquals(table.columns, back?.columns)
        assertEquals(table.rows, back?.rows)
        // CSV has nowhere to put it, and a comment line would stop spreadsheets opening it.
        assertEquals("", back?.name)
    }

    /** The field a note lands in is exactly the field that breaks a naive split. */
    @Test
    fun `a comma inside a value survives csv`() {
        val awkward = DataTable(
            name = "t",
            columns = listOf("slug", "note"),
            rows = listOf(listOf("brake-pad", "Front, rear \"and\" both")),
        )
        val back = TableDocument.read(TableDocument.write(awkward, TableFormat.Csv))
        assertEquals(listOf(listOf("brake-pad", "Front, rear \"and\" both")), back?.rows)
    }

    @Test
    fun `a newline inside a value survives csv`() {
        val awkward = DataTable("t", listOf("note"), listOf(listOf("line one\nline two")))
        val back = TableDocument.read(TableDocument.write(awkward, TableFormat.Csv))
        assertEquals(listOf(listOf("line one\nline two")), back?.rows)
    }

    @Test
    fun `carriage returns from an older exporter read as one line break`() {
        val back = TableDocument.read("slug,note\r\noil-filter,fine\r\n")
        assertEquals(listOf(listOf("oil-filter", "fine")), back?.rows)
    }

    @Test
    fun `a trailing newline does not become an empty row`() {
        val back = TableDocument.read("slug\noil-filter\n\n")
        assertEquals(listOf(listOf("oil-filter")), back?.rows)
    }

    @Test
    fun `excel is xml, escaped, with the sheet named after the table`() {
        val xml = TableDocument.write(
            DataTable("part_prices", listOf("note"), listOf(listOf("A & B <c>"))),
            TableFormat.Excel,
        )
        assertTrue(xml.startsWith("<?xml"), "not xml: ${xml.take(40)}")
        assertTrue("ss:Name=\"part_prices\"" in xml, "sheet not named")
        assertTrue("A &amp; B &lt;c&gt;" in xml, "not escaped")
        assertTrue("A & B <c>" !in xml, "raw value leaked")
    }

    /** Excel refuses the whole file over one, rather than skipping the cell. */
    @Test
    fun `excel drops the control characters xml forbids`() {
        val xml = TableDocument.write(
            DataTable("t", listOf("note"), listOf(listOf("bad\u0001char\u0002here"))),
            TableFormat.Excel,
        )
        assertTrue("badcharhere" in xml, "control characters were not dropped")
    }

    @Test
    fun `something that is neither is refused rather than thrown`() {
        assertNull(TableDocument.read(""))
        assertNull(TableDocument.read("   "))
        assertNull(TableDocument.read("{\"kind\":\"something.else\",\"table\":{}}"))
    }

    @Test
    fun `rows can be read by column name whatever the order`() {
        val reordered = DataTable("t", listOf("b", "a"), listOf(listOf("2", "1")))
        assertEquals(mapOf("b" to "2", "a" to "1"), reordered.rowsAsMaps().single())
    }

    /** A short row is padded rather than throwing — a spreadsheet trims empty trailing cells. */
    @Test
    fun `a row shorter than the header reads as blanks`() {
        val short = DataTable("t", listOf("a", "b", "c"), listOf(listOf("1")))
        assertEquals(mapOf("a" to "1", "b" to "", "c" to ""), short.rowsAsMaps().single())
    }
}
