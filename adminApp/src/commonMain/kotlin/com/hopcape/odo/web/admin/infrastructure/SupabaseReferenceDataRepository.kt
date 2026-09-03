package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import com.hopcape.odo.web.admin.domain.Coverage
import com.hopcape.odo.web.admin.domain.JobPrice
import com.hopcape.odo.web.admin.domain.LabourRate
import com.hopcape.odo.web.admin.domain.PartPrice
import com.hopcape.odo.web.admin.domain.Provenance
import com.hopcape.odo.web.admin.domain.ReferenceDataRepository
import com.hopcape.odo.web.admin.domain.ResolvedBand
import com.hopcape.odo.web.admin.domain.ScheduleItem
import com.hopcape.odo.web.admin.domain.ServiceItem
import com.hopcape.odo.web.admin.domain.VehicleSegment
import com.hopcape.odo.web.admin.domain.WorkshopTier
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The four reference tables and the benchmark RPC, over PostgREST.
 *
 * Reads are unfiltered on status on purpose: the panel is the one caller that has
 * to see drafts, because approving one is impossible if it cannot be listed. The
 * app's own reads filter to approved.
 */
internal class SupabaseReferenceDataRepository(
    private val postgrest: Postgrest,
) : ReferenceDataRepository {

    override suspend fun labourRates(): Either<WebError, List<LabourRate>> =
        postgrest.select(
            table = TABLE_LABOUR,
            serializer = LabourRateRow.serializer(),
            query = "select=$PROVENANCE,city_tier,workshop_tier,paise_per_hour" +
                "&order=city_tier.asc,workshop_tier.asc",
        ).map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun jobPrices(): Either<WebError, List<JobPrice>> =
        postgrest.select(
            table = TABLE_JOB,
            serializer = JobPriceRow.serializer(),
            // The category name is joined rather than looked up client-side: a
            // table of uuids is not a table anybody can proofread.
            query = "select=$PROVENANCE,id,service_category_id,segment,fuel_type," +
                "parts_paise,labour_hours,service_categories(display_name)" +
                "&order=segment.asc",
        ).map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun partPrices(): Either<WebError, List<PartPrice>> =
        postgrest.select(
            table = TABLE_PART,
            serializer = PartPriceRow.serializer(),
            query = "select=$PROVENANCE,id,part_slug,segment,fuel_type,unit,mrp_paise&order=part_slug.asc",
        ).map { rows -> rows.map { it.toDomain() } }

    override suspend fun schedule(): Either<WebError, List<ScheduleItem>> =
        postgrest.select(
            table = TABLE_SCHEDULE,
            serializer = ScheduleRow.serializer(),
            // Default rules first: nulls sort ahead, and the default set is what
            // somebody checks before looking for an exception to it.
            query = "select=$PROVENANCE,id,brand,item_slug,display_name,due_km,due_months" +
                "&order=brand.asc.nullsfirst,item_slug.asc",
        ).map { rows -> rows.map { it.toDomain() } }

    override suspend fun coverage(): Either<WebError, List<Coverage>> =
        postgrest.select(
            table = VIEW_COVERAGE,
            serializer = CoverageRow.serializer(),
            query = "select=table_name,approved_rows,expected_rows",
        ).map { rows -> rows.map { Coverage(it.tableName, it.approvedRows, it.expectedRows) } }

    override suspend fun categories(): Either<WebError, List<ServiceItem>> =
        postgrest.select(
            table = TABLE_CATEGORIES,
            serializer = ServiceCategoryRow.serializer(),
            query = "select=id,slug,display_name&is_active=eq.true&order=display_name.asc",
        ).map { rows ->
            rows.map {
                ServiceItem(
                    id = it.id,
                    slug = it.slug,
                    name = it.displayName,
                    intervalKm = null,
                    intervalMonths = null,
                    benchmarkPaise = null,
                    notes = null,
                    isActive = true,
                )
            }
        }

    override suspend fun saveLabourRate(rate: LabourRate): Either<WebError, Unit> =
        postgrest.upsert(
            table = TABLE_LABOUR,
            onConflict = "city_tier,workshop_tier",
            serializer = IdOnly.serializer(),
            body = """{"city_tier":${rate.cityTier},""" +
                """"workshop_tier":"${rate.workshopTier.id}",""" +
                """"paise_per_hour":${rate.paisePerHour}${rate.provenance.fields()}}""",
        ).map { }

    override suspend fun saveJobPrice(price: JobPrice): Either<WebError, Unit> =
        postgrest.upsert(
            table = TABLE_JOB,
            serializer = IdOnly.serializer(),
            body = buildString {
                append("{")
                if (price.id.isNotBlank()) append(""""id":"${price.id}",""")
                append(""""service_category_id":"${price.categoryId}",""")
                append(""""segment":"${price.segment.id}",""")
                append(""""fuel_type":${price.fuelType.jsonOrNull()},""")
                append(""""parts_paise":${price.partsPaise},""")
                append(""""labour_hours":${price.labourHours}""")
                append(price.provenance.fields())
                append("}")
            },
        ).map { }

    override suspend fun savePartPrice(price: PartPrice): Either<WebError, Unit> =
        postgrest.upsert(
            table = TABLE_PART,
            serializer = IdOnly.serializer(),
            body = buildString {
                append("{")
                if (price.id.isNotBlank()) append(""""id":"${price.id}",""")
                append(""""part_slug":"${price.partSlug.jsonEscaped()}",""")
                append(""""segment":${price.segment?.id.jsonOrNull()},""")
                append(""""fuel_type":${price.fuelType.jsonOrNull()},""")
                append(""""unit":"${price.unit}",""")
                append(""""mrp_paise":${price.mrpPaise}""")
                append(price.provenance.fields())
                append("}")
            },
        ).map { }

    override suspend fun saveScheduleItem(item: ScheduleItem): Either<WebError, Unit> =
        postgrest.upsert(
            table = TABLE_SCHEDULE,
            serializer = IdOnly.serializer(),
            body = buildString {
                append("{")
                if (item.id.isNotBlank()) append(""""id":"${item.id}",""")
                append(""""brand":${item.brand.jsonOrNull()},""")
                append(""""item_slug":"${item.itemSlug.jsonEscaped()}",""")
                append(""""display_name":"${item.displayName.jsonEscaped()}",""")
                append(""""due_km":${item.dueKm ?: "null"},""")
                append(""""due_months":${item.dueMonths ?: "null"}""")
                append(item.provenance.fields())
                append("}")
            },
        ).map { }

    override suspend fun setStatus(table: String, id: String, approved: Boolean): Either<WebError, Unit> =
        postgrest.patch(
            table = table,
            query = if (table == TABLE_LABOUR) labourKeyQuery(id) else "id=eq.$id",
            body = """{"status":"${if (approved) Provenance.APPROVED else Provenance.DRAFT}"}""",
        )

    override suspend fun resolve(
        categorySlug: String,
        city: String,
        segment: VehicleSegment,
        workshopTier: WorkshopTier,
    ): Either<WebError, ResolvedBand?> =
        postgrest.rpc(
            name = RPC_BENCHMARK,
            serializer = BandRow.serializer(),
            body = """{"p_category":"${categorySlug.jsonEscaped()}",""" +
                """"p_city":"${city.jsonEscaped()}",""" +
                """"p_segment":"${segment.id}",""" +
                """"p_tier":"${workshopTier.id}"}""",
            // No row is a real answer: the category has no approved job price, so
            // the app would say nothing here too.
        ).map { rows -> rows.firstOrNull()?.toDomain() }

    /** `labour_rates` has no id — its key is the pair, encoded as "tier:workshop". */
    private fun labourKeyQuery(id: String): String {
        val (tier, workshop) = id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return "city_tier=eq.$tier&workshop_tier=eq.$workshop"
    }

    private companion object {
        const val TABLE_LABOUR = "labour_rates"
        const val TABLE_JOB = "job_prices"
        const val TABLE_PART = "part_prices"
        const val TABLE_SCHEDULE = "service_schedule"
        const val TABLE_CATEGORIES = "service_categories"
        const val VIEW_COVERAGE = "reference_data_coverage"
        const val RPC_BENCHMARK = "get_fairness_benchmark"
        const val PROVENANCE = "source_url,source_note,verified_on,status"
    }
}

/** The provenance columns, as a JSON tail every save appends. */
private fun Provenance.fields(): String = buildString {
    append(""","source_url":${sourceUrl.jsonOrNull()}""")
    append(""","source_note":${sourceNote.jsonOrNull()}""")
    append(""","verified_on":${verifiedOn.jsonOrNull()}""")
    append(""","status":"$status"""")
}

/**
 * A nullable string as JSON.
 *
 * Explicit `null` rather than an omitted key. Omitting one is what leaves a
 * cleared field unchanged on the server, and PostgREST rejects a batch whose
 * objects do not share a key set.
 */
private fun String?.jsonOrNull(): String = if (this == null) "null" else "\"${jsonEscaped()}\""

@Serializable
private data class IdOnly(val id: String? = null)

@Serializable
private data class LabourRateRow(
    @SerialName("city_tier") val cityTier: Int,
    @SerialName("workshop_tier") val workshopTier: String,
    @SerialName("paise_per_hour") val paisePerHour: Long,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_note") val sourceNote: String? = null,
    @SerialName("verified_on") val verifiedOn: String? = null,
    val status: String = Provenance.DRAFT,
) {
    // Null for a tier this build has no word for. The database may grow one before
    // the panel does, and dropping it beats drawing a row nobody can edit.
    fun toDomain(): LabourRate? = WorkshopTier.ofId(workshopTier)?.let {
        LabourRate(cityTier, it, paisePerHour, Provenance(sourceUrl, sourceNote, verifiedOn, status))
    }
}

@Serializable
private data class JoinedCategory(@SerialName("display_name") val displayName: String)

@Serializable
private data class JobPriceRow(
    val id: String,
    @SerialName("service_category_id") val categoryId: String,
    val segment: String,
    @SerialName("fuel_type") val fuelType: String? = null,
    @SerialName("parts_paise") val partsPaise: Long,
    @SerialName("labour_hours") val labourHours: Double,
    @SerialName("service_categories") val category: JoinedCategory? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_note") val sourceNote: String? = null,
    @SerialName("verified_on") val verifiedOn: String? = null,
    val status: String = Provenance.DRAFT,
) {
    fun toDomain(): JobPrice? = VehicleSegment.ofId(segment)?.let {
        JobPrice(
            id = id,
            categoryId = categoryId,
            categoryName = category?.displayName ?: categoryId,
            segment = it,
            fuelType = fuelType,
            partsPaise = partsPaise,
            labourHours = labourHours,
            provenance = Provenance(sourceUrl, sourceNote, verifiedOn, status),
        )
    }
}

@Serializable
private data class PartPriceRow(
    val id: String,
    @SerialName("part_slug") val partSlug: String,
    val segment: String? = null,
    @SerialName("fuel_type") val fuelType: String? = null,
    val unit: String,
    @SerialName("mrp_paise") val mrpPaise: Long,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_note") val sourceNote: String? = null,
    @SerialName("verified_on") val verifiedOn: String? = null,
    val status: String = Provenance.DRAFT,
) {
    fun toDomain() = PartPrice(
        id = id,
        partSlug = partSlug,
        segment = segment?.let(VehicleSegment::ofId),
        fuelType = fuelType,
        unit = unit,
        mrpPaise = mrpPaise,
        provenance = Provenance(sourceUrl, sourceNote, verifiedOn, status),
    )
}

@Serializable
private data class ScheduleRow(
    val id: String,
    val brand: String? = null,
    @SerialName("item_slug") val itemSlug: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("due_km") val dueKm: Int? = null,
    @SerialName("due_months") val dueMonths: Int? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_note") val sourceNote: String? = null,
    @SerialName("verified_on") val verifiedOn: String? = null,
    val status: String = Provenance.DRAFT,
) {
    fun toDomain() = ScheduleItem(
        id = id,
        brand = brand,
        itemSlug = itemSlug,
        displayName = displayName,
        dueKm = dueKm,
        dueMonths = dueMonths,
        provenance = Provenance(sourceUrl, sourceNote, verifiedOn, status),
    )
}

@Serializable
private data class CoverageRow(
    @SerialName("table_name") val tableName: String,
    @SerialName("approved_rows") val approvedRows: Int,
    @SerialName("expected_rows") val expectedRows: Int,
)

@Serializable
private data class ServiceCategoryRow(
    val id: String,
    val slug: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
private data class BandRow(
    @SerialName("avg_paise") val avgPaise: Long? = null,
    val p25: Long? = null,
    val p75: Long? = null,
    @SerialName("sample_size") val sampleSize: Long = 0,
    val scope: String? = null,
    val basis: String? = null,
) {
    fun toDomain(): ResolvedBand? = avgPaise?.let {
        ResolvedBand(it, p25 ?: it, p75 ?: it, sampleSize, scope.orEmpty(), basis.orEmpty())
    }
}
