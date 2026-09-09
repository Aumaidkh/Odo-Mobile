package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/**
 * The pricing axis, matching the `vehicle_segment` enum.
 *
 * Brand is deliberately absent: Swift, i20, Baleno and Tiago are one row.
 */
enum class VehicleSegment(val id: String) {
    Hatchback("hatchback"),
    Sedan("sedan"),
    Suv("suv"),
    Muv("muv"),
    ;

    companion object {
        fun ofId(id: String): VehicleSegment? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Where the work was done, matching the `workshop_tier` enum.
 *
 * Without this axis a local garage always reads "under" and an authorised centre
 * always reads "over", so every price verdict is wrong in a predictable direction.
 */
enum class WorkshopTier(val id: String) {
    Authorised("authorised"),
    MultiBrand("multi_brand"),
    Local("local"),
    ;

    companion object {
        fun ofId(id: String): WorkshopTier? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Where a number came from and whether it may be served.
 *
 * A figure with no provenance cannot be re-checked in six months, and these go
 * stale. Only [APPROVED] rows are ever read by the app.
 */
data class Provenance(
    val sourceUrl: String? = null,
    val sourceNote: String? = null,
    val verifiedOn: String? = null,
    val status: String = DRAFT,
) {
    val isApproved: Boolean get() = status == APPROVED

    companion object {
        const val DRAFT = "draft"
        const val APPROVED = "approved"
    }
}

/** Labour cost per hour for one city tier and workshop tier. Nine rows in total. */
data class LabourRate(
    val cityTier: Int,
    val workshopTier: WorkshopTier,
    val paisePerHour: Long,
    val provenance: Provenance,
)

/**
 * What one job costs before labour, and how long it takes.
 *
 * [fuelType] is null for the jobs that do not vary by fuel, which is most of them.
 */
data class JobPrice(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val segment: VehicleSegment,
    val fuelType: String?,
    val partsPaise: Long,
    val labourHours: Double,
    val provenance: Provenance,
)

/** A part's MRP, for the few jobs the OEM estimators do not itemise. */
data class PartPrice(
    val id: String,
    val partSlug: String,
    val segment: VehicleSegment?,
    val fuelType: String?,
    val unit: String,
    val mrpPaise: Long,
    val provenance: Provenance,
)

/**
 * When the maker says a job is due.
 *
 * A null [brand] is the default rule set, used by every brand without an
 * exception row of its own.
 */
data class ScheduleItem(
    val id: String,
    val brand: String?,
    val itemSlug: String,
    val displayName: String,
    val dueKm: Int?,
    val dueMonths: Int?,
    val provenance: Provenance,
)

/** Approved rows against the hand-entry budget, per table. */
data class Coverage(
    val tableName: String,
    val approvedRows: Int,
    val expectedRows: Int,
) {
    val isComplete: Boolean get() = approvedRows >= expectedRows
}

/**
 * What the app would answer for one lookup.
 *
 * [scope] names the rung of the widening ladder that answered, which is how a
 * typo is caught here instead of at a service counter.
 */
data class ResolvedBand(
    val avgPaise: Long,
    val p25: Long,
    val p75: Long,
    val sampleSize: Long,
    val scope: String,
    val basis: String,
)

/**
 * The reference data behind every price answer.
 *
 * Writes are refused by RLS unless the session holds `fairness.write`. Nothing
 * here checks that: a client that decided for itself could be patched to decide
 * differently.
 */
interface PriceBookRepository {

    suspend fun labourRates(): Either<WebError, List<LabourRate>>

    suspend fun jobPrices(): Either<WebError, List<JobPrice>>

    suspend fun partPrices(): Either<WebError, List<PartPrice>>

    suspend fun schedule(): Either<WebError, List<ScheduleItem>>

    suspend fun coverage(): Either<WebError, List<Coverage>>

    /** The categories a job price can be keyed to, for the editor's picker. */
    suspend fun categories(): Either<WebError, List<ServiceItem>>

    suspend fun saveLabourRate(rate: LabourRate): Either<WebError, Unit>

    suspend fun saveJobPrice(price: JobPrice): Either<WebError, Unit>

    suspend fun savePartPrice(price: PartPrice): Either<WebError, Unit>

    suspend fun saveScheduleItem(item: ScheduleItem): Either<WebError, Unit>

    /**
     * Move a row between draft and approved.
     *
     * [table] is the physical table name; these four have no common id shape, so
     * a single method takes the name rather than four near-identical ones.
     */
    suspend fun setStatus(table: String, id: String, approved: Boolean): Either<WebError, Unit>

    /**
     * Remove a row for good.
     *
     * Not a soft delete: nothing outside this panel reads these tables by id, so a
     * tombstone would only be a row somebody has to keep scrolling past. Retiring a figure
     * that is merely wrong is [setStatus] — an unapproved row is already unserved.
     *
     * [table] is the physical table name, as in [setStatus]. Labour rates are not deletable
     * — they are a fixed three-by-three grid, and a missing cell is a hole every lookup in
     * that city tier falls through.
     */
    suspend fun delete(table: String, id: String): Either<WebError, Unit>

    /**
     * Write a whole table in, matched on the key the table is keyed by.
     *
     * Nothing is removed: an import means "these should exist here", not "make this table
     * look like that file". A file exported before somebody added a row would otherwise
     * delete it.
     *
     * @return how many rows the database wrote.
     */
    suspend fun importLabour(rates: List<LabourRate>): Either<WebError, Int>

    suspend fun importJobs(prices: List<JobPrice>): Either<WebError, Int>

    suspend fun importParts(prices: List<PartPrice>): Either<WebError, Int>

    suspend fun importSchedule(items: List<ScheduleItem>): Either<WebError, Int>

    /**
     * Run the same lookup the app runs.
     *
     * Returns null when the category has no approved job price, which is the
     * deliberate silence for clutch, tyres, battery, brake discs and bodywork.
     */
    suspend fun resolve(
        categorySlug: String,
        city: String,
        segment: VehicleSegment,
        workshopTier: WorkshopTier,
    ): Either<WebError, ResolvedBand?>
}
