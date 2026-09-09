package com.hopcape.odo.web.admin.domain

/**
 * The four price-book tables as columns and rows, and back again.
 *
 * Column names match the database, so an exported file reads like the table it came from and
 * a row pasted from a SQL client imports without renaming anything.
 *
 * Money travels as rupees, not paise. A spreadsheet is where these get edited, and nobody
 * types 280000 for two thousand eight hundred; the conversion is here, in one place, rather
 * than in whoever is reading the file.
 */
object PriceBookTables {

    const val LABOUR: String = "labour_rates"
    const val JOBS: String = "job_prices"
    const val PARTS: String = "part_prices"
    const val SCHEDULE: String = "service_schedule"

    /** A row that would not import, named so the panel can say which one and why. */
    data class Rejected(val rowNumber: Int, val reason: String)

    /** What a file turned into: the rows that read, and the ones that did not. */
    data class Parsed<T>(val rows: List<T>, val rejected: List<Rejected>)

    // Labour rates

    fun labourTable(rates: List<LabourRate>) = DataTable(
        name = LABOUR,
        columns = listOf("city_tier", "workshop_tier", "rate_per_hour", "source_url", "source_note", "verified_on", "status"),
        rows = rates.map {
            listOf(
                it.cityTier.toString(),
                it.workshopTier.id,
                it.paisePerHour.rupees(),
                it.provenance.sourceUrl.orEmpty(),
                it.provenance.sourceNote.orEmpty(),
                it.provenance.verifiedOn.orEmpty(),
                it.provenance.status,
            )
        },
    )

    fun readLabour(table: DataTable): Parsed<LabourRate> = table.parse { row ->
        val tier = row["city_tier"]?.trim()?.toIntOrNull()
            ?: return@parse "city_tier must be a number".left()
        if (tier !in 1..3) return@parse "city_tier must be 1, 2 or 3".left()
        val workshop = WorkshopTier.ofId(row["workshop_tier"].orEmpty().trim())
            ?: return@parse "workshop_tier must be one of ${WorkshopTier.entries.joinToString { it.id }}".left()
        val paise = row["rate_per_hour"].paiseOrNull()
            ?: return@parse "rate_per_hour must be a number".left()
        LabourRate(tier, workshop, paise, row.provenance()).right()
    }

    // Job prices

    fun jobsTable(prices: List<JobPrice>) = DataTable(
        name = JOBS,
        columns = listOf("id", "category_id", "category_name", "segment", "fuel_type", "parts_cost", "labour_hours", "source_url", "source_note", "verified_on", "status"),
        rows = prices.map {
            listOf(
                it.id,
                it.categoryId,
                it.categoryName,
                it.segment.id,
                it.fuelType.orEmpty(),
                it.partsPaise.rupees(),
                it.labourHours.toString(),
                it.provenance.sourceUrl.orEmpty(),
                it.provenance.sourceNote.orEmpty(),
                it.provenance.verifiedOn.orEmpty(),
                it.provenance.status,
            )
        },
    )

    /**
     * A blank `id` is a new row, which is what a line typed into a spreadsheet looks like.
     * The database fills it in.
     */
    fun readJobs(table: DataTable): Parsed<JobPrice> = table.parse { row ->
        val category = row["category_id"].orEmpty().trim()
        if (category.isEmpty()) return@parse "category_id is required".left()
        val segment = VehicleSegment.ofId(row["segment"].orEmpty().trim())
            ?: return@parse "segment must be one of ${VehicleSegment.entries.joinToString { it.id }}".left()
        val parts = row["parts_cost"].paiseOrNull()
            ?: return@parse "parts_cost must be a number".left()
        val hours = row["labour_hours"]?.trim()?.toDoubleOrNull()
            ?: return@parse "labour_hours must be a number".left()
        JobPrice(
            id = row["id"].orEmpty().trim(),
            categoryId = category,
            categoryName = row["category_name"].orEmpty().trim(),
            segment = segment,
            fuelType = row["fuel_type"].blankToNull(),
            partsPaise = parts,
            labourHours = hours,
            provenance = row.provenance(),
        ).right()
    }

    // Part prices

    fun partsTable(prices: List<PartPrice>) = DataTable(
        name = PARTS,
        columns = listOf("id", "part_slug", "segment", "fuel_type", "unit", "mrp", "source_url", "source_note", "verified_on", "status"),
        rows = prices.map {
            listOf(
                it.id,
                it.partSlug,
                it.segment?.id.orEmpty(),
                it.fuelType.orEmpty(),
                it.unit,
                it.mrpPaise.rupees(),
                it.provenance.sourceUrl.orEmpty(),
                it.provenance.sourceNote.orEmpty(),
                it.provenance.verifiedOn.orEmpty(),
                it.provenance.status,
            )
        },
    )

    fun readParts(table: DataTable): Parsed<PartPrice> = table.parse { row ->
        val slug = row["part_slug"].orEmpty().trim()
        if (slug.isEmpty()) return@parse "part_slug is required".left()
        // Null is a real answer here: a part priced the same across segments.
        val segmentId = row["segment"].blankToNull()
        val segment = segmentId?.let {
            VehicleSegment.ofId(it.trim()) ?: return@parse "segment must be blank or one of ${VehicleSegment.entries.joinToString { s -> s.id }}".left()
        }
        val mrp = row["mrp"].paiseOrNull() ?: return@parse "mrp must be a number".left()
        PartPrice(
            id = row["id"].orEmpty().trim(),
            partSlug = slug,
            segment = segment,
            fuelType = row["fuel_type"].blankToNull(),
            unit = row["unit"].orEmpty().trim().ifEmpty { "each" },
            mrpPaise = mrp,
            provenance = row.provenance(),
        ).right()
    }

    // Service schedule

    fun scheduleTable(items: List<ScheduleItem>) = DataTable(
        name = SCHEDULE,
        columns = listOf("id", "brand", "item_slug", "display_name", "due_km", "due_months", "source_url", "source_note", "verified_on", "status"),
        rows = items.map {
            listOf(
                it.id,
                it.brand.orEmpty(),
                it.itemSlug,
                it.displayName,
                it.dueKm?.toString().orEmpty(),
                it.dueMonths?.toString().orEmpty(),
                it.provenance.sourceUrl.orEmpty(),
                it.provenance.sourceNote.orEmpty(),
                it.provenance.verifiedOn.orEmpty(),
                it.provenance.status,
            )
        },
    )

    fun readSchedule(table: DataTable): Parsed<ScheduleItem> = table.parse { row ->
        val slug = row["item_slug"].orEmpty().trim()
        if (slug.isEmpty()) return@parse "item_slug is required".left()
        val km = row["due_km"].intOrNull()
        val months = row["due_months"].intOrNull()
        // A rule due at neither is a rule that never fires.
        if (km == null && months == null) return@parse "one of due_km or due_months is required".left()
        ScheduleItem(
            id = row["id"].orEmpty().trim(),
            // Blank is the default rule set every brand without an exception uses.
            brand = row["brand"].blankToNull(),
            itemSlug = slug,
            displayName = row["display_name"].orEmpty().trim().ifEmpty { slug },
            dueKm = km,
            dueMonths = months,
            provenance = row.provenance(),
        ).right()
    }

    // Shared

    /**
     * Rows are read one at a time and a bad one is set aside rather than failing the file.
     * A hundred-row paste with one typo should import ninety-nine and say which one it
     * could not.
     */
    private fun <T> DataTable.parse(read: (Map<String, String>) -> Read<T>): Parsed<T> {
        val good = mutableListOf<T>()
        val bad = mutableListOf<Rejected>()
        rowsAsMaps().forEachIndexed { index, row ->
            // +2: the header is row 1, and a spreadsheet counts from 1.
            when (val result = read(row)) {
                is Read.Ok -> good += result.value
                is Read.Bad -> bad += Rejected(index + 2, result.reason)
            }
        }
        return Parsed(good, bad)
    }

    private sealed interface Read<out T> {
        data class Ok<T>(val value: T) : Read<T>
        data class Bad(val reason: String) : Read<Nothing>
    }

    private fun <T> T.right(): Read<T> = Read.Ok(this)
    private fun String.left(): Read<Nothing> = Read.Bad(this)

    private fun Map<String, String>.provenance() = Provenance(
        sourceUrl = this["source_url"].blankToNull(),
        sourceNote = this["source_note"].blankToNull(),
        verifiedOn = this["verified_on"].blankToNull(),
        // A row arrives as a draft unless the file says otherwise. Nothing the app serves
        // should become servable because somebody pasted a spreadsheet.
        status = if (this["status"]?.trim() == Provenance.APPROVED) Provenance.APPROVED else Provenance.DRAFT,
    )

    /** Paise for storage, rupees in the file. Two decimals are accepted and rounded. */
    private fun String?.paiseOrNull(): Long? {
        val cleaned = this?.trim()?.replace(",", "")?.removePrefix("Rs.")?.removePrefix("₹")?.trim()
        val rupees = cleaned?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: return null
        if (rupees < 0) return null
        return (rupees * 100).toLong()
    }

    private fun Long.rupees(): String {
        val whole = this / 100
        val paise = this % 100
        return if (paise == 0L) whole.toString() else "$whole.${paise.toString().padStart(2, '0')}"
    }

    private fun String?.intOrNull(): Int? = this?.trim()?.replace(",", "")?.toIntOrNull()

    private fun String?.blankToNull(): String? = this?.trim()?.ifBlank { null }
}
