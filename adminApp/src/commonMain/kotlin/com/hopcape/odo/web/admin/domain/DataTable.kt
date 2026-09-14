package com.hopcape.odo.web.admin.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A table as column names and rows of text.
 *
 * Every format here is a table, so they all read and write this one shape rather than each
 * knowing about labour rates. Values are text because that is what a spreadsheet returns:
 * typing happens once, on the way back into the domain, where a bad cell can be reported.
 */
data class DataTable(
    val name: String,
    val columns: List<String>,
    val rows: List<List<String>>,
) {
    /** One row addressed by column name, for a reader that must not depend on column order. */
    fun rowsAsMaps(): List<Map<String, String>> =
        rows.map { row -> columns.mapIndexed { i, c -> c to row.getOrElse(i) { "" } }.toMap() }
}

/**
 * How a table leaves the panel.
 *
 * JSON is the exact one — it round-trips. CSV is the one a spreadsheet opens. Excel is
 * SpreadsheetML, an XML dialect Excel, Numbers and Sheets all open, chosen because a real
 * `.xlsx` is a ZIP and nothing here can write one.
 */
enum class TableFormat(val extension: String, val mime: String) {
    Json("json", "application/json"),
    Csv("csv", "text/csv"),
    Excel("xls", "application/vnd.ms-excel"),
}

object TableDocument {

    /** What a file picker should offer. Excel is absent: see [read]. */
    const val ACCEPT: String = ".json,.csv,application/json,text/csv"

    fun write(table: DataTable, format: TableFormat): String = when (format) {
        TableFormat.Json -> writeJson(table)
        TableFormat.Csv -> writeCsv(table)
        TableFormat.Excel -> writeExcel(table)
    }

    /**
     * Reads a file back, JSON or CSV, deciding by content rather than by name — a file
     * renamed on the way through a mail client still imports.
     *
     * Excel is deliberately not read. What this writes is SpreadsheetML, but a workbook the
     * owner edits and saves as `.xls` comes back as binary BIFF, so accepting the extension
     * would work for untouched files and fail for exactly the ones worth importing. Saving
     * as CSV from the same menu is the honest path, and [ACCEPT] says so by omission.
     */
    fun read(text: String): DataTable? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("{")) readJson(trimmed) else readCsv(text)
    }

    // JSON

    private fun writeJson(table: DataTable): String =
        JSON.encodeToString(TableFile.serializer(), TableFile(table = table.toRow()))

    private fun readJson(text: String): DataTable? {
        val file = runCatching { JSON.decodeFromString(TableFile.serializer(), text) }.getOrNull()
            ?: return null
        if (file.kind != KIND) return null
        return DataTable(file.table.name, file.table.columns, file.table.rows)
    }

    // CSV

    /**
     * The table name is not in the file. CSV has nowhere to put it, and a comment line would
     * stop every spreadsheet from opening it as a table.
     */
    private fun writeCsv(table: DataTable): String = buildString {
        appendLine(table.columns.joinToString(",") { it.csvEscaped() })
        table.rows.forEach { row -> appendLine(row.joinToString(",") { it.csvEscaped() }) }
    }

    private fun readCsv(text: String): DataTable? {
        val rows = parseCsv(text).filter { row -> row.any(String::isNotBlank) }
        if (rows.isEmpty()) return null
        return DataTable(name = "", columns = rows.first(), rows = rows.drop(1))
    }

    /**
     * A field is quoted when it holds a comma, a quote or a newline; a quote inside one is
     * doubled. Anything less and a note with a comma in it silently becomes two columns.
     */
    private fun String.csvEscaped(): String =
        if (none { it == ',' || it == '"' || it == '\n' || it == '\r' }) this
        else "\"" + replace("\"", "\"\"") + "\""

    /**
     * A full parser rather than `split(",")`, because a quoted field may itself hold a comma
     * or a line break, and those are exactly the fields a note lands in.
     */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.clear()
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> endField()
                // A CRLF counts once. A bare CR is a line break too, from older exporters.
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    endRow()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    // Excel

    /**
     * SpreadsheetML 2003. Every cell is written as text, so a part number like `04152-YZZA5`
     * keeps its shape instead of being reformatted into something else on open.
     */
    private fun writeExcel(table: DataTable): String = buildString {
        appendLine("<?xml version=\"1.0\"?>")
        appendLine("<?mso-application progid=\"Excel.Sheet\"?>")
        appendLine("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"")
        appendLine("          xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">")
        appendLine("<Worksheet ss:Name=\"" + table.name.sheetName().xmlEscaped() + "\"><Table>")
        appendLine(table.columns.excelRow())
        table.rows.forEach { appendLine(it.excelRow()) }
        appendLine("</Table></Worksheet></Workbook>")
    }

    private fun List<String>.excelRow(): String =
        joinToString("", prefix = "<Row>", postfix = "</Row>") {
            "<Cell><Data ss:Type=\"String\">" + it.xmlEscaped() + "</Data></Cell>"
        }

    /** Excel refuses a sheet name over 31 characters, or an empty one. */
    private fun String.sheetName(): String = ifBlank { "Sheet1" }.take(31)

    private fun String.xmlEscaped(): String = buildString(length) {
        this@xmlEscaped.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\t' || c == '\n' || c == '\r' -> append(c)
                // XML 1.0 allows no other control character, and Excel refuses the whole
                // file over one of them rather than skipping the cell.
                c.code < 0x20 -> Unit
                else -> append(c)
            }
        }
    }

    private const val KIND = "odo.table"

    private fun DataTable.toRow() = TableRow(name, columns, rows)

    private val JSON = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
}

@Serializable
private data class TableFile(
    val kind: String = "odo.table",
    val version: Int = 1,
    val table: TableRow,
)

@Serializable
private data class TableRow(
    val name: String,
    val columns: List<String>,
    val rows: List<List<String>>,
)
