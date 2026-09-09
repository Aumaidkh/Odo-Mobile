package com.hopcape.odo.web.admin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceBookTablesTest {

    private val approved = Provenance("https://example.test/p", "OEM list", "2026-08-01", Provenance.APPROVED)

    private val rate = LabourRate(cityTier = 1, workshopTier = WorkshopTier.Authorised, paisePerHour = 95_000, provenance = approved)
    private val job = JobPrice("job-1", "cat-1", "Engine oil change", VehicleSegment.Hatchback, "petrol", 280_000, 1.5, approved)
    private val part = PartPrice("part-1", "oil-filter", VehicleSegment.Suv, null, "each", 45_050, approved)
    private val item = ScheduleItem("sch-1", "Maruti", "engine-oil", "Engine oil", 10_000, 12, approved)

    private fun <T> roundTrip(table: DataTable, read: (DataTable) -> PriceBookTables.Parsed<T>): T {
        val back = TableDocument.read(TableDocument.write(table, TableFormat.Csv))!!
        val parsed = read(back)
        assertEquals(emptyList(), parsed.rejected, "rows were rejected")
        return parsed.rows.single()
    }

    @Test
    fun `a labour rate survives csv`() {
        val back = roundTrip(PriceBookTables.labourTable(listOf(rate)), PriceBookTables::readLabour)
        assertEquals(rate, back)
    }

    @Test
    fun `a job price survives csv`() {
        val back = roundTrip(PriceBookTables.jobsTable(listOf(job)), PriceBookTables::readJobs)
        assertEquals(job, back)
    }

    @Test
    fun `a part price survives csv, null segment and all`() {
        val noSegment = part.copy(segment = null)
        val back = roundTrip(PriceBookTables.partsTable(listOf(noSegment)), PriceBookTables::readParts)
        assertEquals(noSegment, back)
    }

    @Test
    fun `a schedule item survives csv, and a blank brand is the default rule set`() {
        val default = item.copy(brand = null)
        val back = roundTrip(PriceBookTables.scheduleTable(listOf(default)), PriceBookTables::readSchedule)
        assertEquals(default, back)
    }

    /** Money is rupees in the file. Nobody types 280000 for two thousand eight hundred. */
    @Test
    fun `money is written as rupees and read back as paise`() {
        val table = PriceBookTables.partsTable(listOf(part))
        assertEquals("450.50", table.rows.single()[table.columns.indexOf("mrp")])
        assertEquals(45_050, roundTrip(table, PriceBookTables::readParts).mrpPaise)
    }

    @Test
    fun `a rupee sign, commas and spaces in a figure are read`() {
        val typed = DataTable(
            name = PriceBookTables.PARTS,
            columns = listOf("part_slug", "unit", "mrp"),
            rows = listOf(listOf("oil-filter", "each", " Rs. 1,250 ")),
        )
        assertEquals(125_000, PriceBookTables.readParts(typed).rows.single().mrpPaise)
    }

    /**
     * A hundred-row paste with one typo should land ninety-nine rows and name the one it
     * could not, by the number the spreadsheet shows.
     */
    @Test
    fun `one bad row is set aside, not the file`() {
        val typed = DataTable(
            name = PriceBookTables.PARTS,
            columns = listOf("part_slug", "unit", "mrp"),
            rows = listOf(
                listOf("oil-filter", "each", "450"),
                listOf("air-filter", "each", "not a number"),
                listOf("cabin-filter", "each", "700"),
            ),
        )
        val parsed = PriceBookTables.readParts(typed)
        assertEquals(2, parsed.rows.size)
        assertEquals(1, parsed.rejected.size)
        // Header is row 1, so the second data row is row 3.
        assertEquals(3, parsed.rejected.single().rowNumber)
        assertTrue("mrp" in parsed.rejected.single().reason, parsed.rejected.single().reason)
    }

    @Test
    fun `a row missing its key is rejected`() {
        val typed = DataTable(PriceBookTables.PARTS, listOf("part_slug", "mrp"), listOf(listOf("", "450")))
        assertEquals("part_slug is required", PriceBookTables.readParts(typed).rejected.single().reason)
    }

    @Test
    fun `a schedule row due at neither km nor months is rejected`() {
        val typed = DataTable(
            PriceBookTables.SCHEDULE,
            listOf("item_slug", "display_name", "due_km", "due_months"),
            listOf(listOf("engine-oil", "Engine oil", "", "")),
        )
        assertTrue("due_km" in PriceBookTables.readSchedule(typed).rejected.single().reason)
    }

    @Test
    fun `an unknown segment is rejected rather than silently defaulted`() {
        val typed = DataTable(
            PriceBookTables.JOBS,
            listOf("category_id", "segment", "parts_cost", "labour_hours"),
            listOf(listOf("cat-1", "spaceship", "2800", "1.5")),
        )
        assertTrue("segment" in PriceBookTables.readJobs(typed).rejected.single().reason)
    }

    @Test
    fun `a labour rate outside the three city tiers is rejected`() {
        val typed = DataTable(
            PriceBookTables.LABOUR,
            listOf("city_tier", "workshop_tier", "rate_per_hour"),
            listOf(listOf("9", "local", "500")),
        )
        assertTrue("city_tier" in PriceBookTables.readLabour(typed).rejected.single().reason)
    }

    /**
     * Nothing the app serves should become servable because somebody pasted a spreadsheet.
     * An approved row has to be approved on the screen.
     */
    @Test
    fun `a row with no status arrives as a draft`() {
        val typed = DataTable(PriceBookTables.PARTS, listOf("part_slug", "mrp"), listOf(listOf("oil-filter", "450")))
        assertEquals(Provenance.DRAFT, PriceBookTables.readParts(typed).rows.single().provenance.status)
    }

    @Test
    fun `an unrecognised status is not approved`() {
        val typed = DataTable(
            PriceBookTables.PARTS,
            listOf("part_slug", "mrp", "status"),
            listOf(listOf("oil-filter", "450", "APPROVED PLEASE")),
        )
        assertEquals(Provenance.DRAFT, PriceBookTables.readParts(typed).rows.single().provenance.status)
    }

    /** A blank id is a line typed into a spreadsheet; the database fills it in. */
    @Test
    fun `a row with no id imports as a new one`() {
        val typed = DataTable(PriceBookTables.PARTS, listOf("part_slug", "mrp"), listOf(listOf("oil-filter", "450")))
        assertEquals("", PriceBookTables.readParts(typed).rows.single().id)
    }

    @Test
    fun `blank provenance columns read as absent, not as empty text`() {
        val typed = DataTable(
            PriceBookTables.PARTS,
            listOf("part_slug", "mrp", "source_url", "source_note", "verified_on"),
            listOf(listOf("oil-filter", "450", "", "  ", "")),
        )
        val provenance = PriceBookTables.readParts(typed).rows.single().provenance
        assertNull(provenance.sourceUrl)
        assertNull(provenance.sourceNote)
        assertNull(provenance.verifiedOn)
    }
}
