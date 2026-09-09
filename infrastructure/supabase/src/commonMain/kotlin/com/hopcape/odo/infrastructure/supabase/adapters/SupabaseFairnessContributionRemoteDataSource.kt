package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.benchmark.FairnessContributionDto
import com.hopcape.odo.core.data.benchmark.FairnessContributionRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Adds checked prices to `fairness_data_points`.
 *
 * **The consent gate is the server's.** The table's insert policy requires the owner's
 * `shares_prices` to be set, so a client that has not been given permission is refused rather
 * than trusted to ask. Nothing here checks it, and nothing here would be believed if it did.
 *
 * The lookup ids are resolved here because the table keys on them and the client holds only a
 * slug and a city name. Both reference tables are small, public and read once per batch — a
 * lookup per row would be a request per line of a bill.
 *
 * A row whose category or city cannot be resolved is dropped, not guessed. A price filed
 * against the wrong job is worse than a price never filed: it is a number that comes back to
 * some other owner as a band.
 */
internal class SupabaseFairnessContributionRemoteDataSource(
    private val postgrest: PostgrestClient,
) : FairnessContributionRemoteDataSource {

    override suspend fun contribute(observations: List<FairnessContributionDto>) {
        if (observations.isEmpty()) return
        val categories = idsBySlug()
        val cities = cityIdsByName()

        val rows = observations.mapNotNull { row ->
            PoolDataPointRow(
                serviceCategoryId = categories[row.categorySlug] ?: return@mapNotNull null,
                cityId = cities[row.city.lowercase()] ?: return@mapNotNull null,
                carMake = row.carMake,
                fuelType = row.fuel,
                amountPaise = row.amountPaise,
                workshopTier = row.workshopTier,
                segment = row.segment,
                source = SOURCE_BILL_CHECK,
            )
        }
        if (rows.isEmpty()) return

        postgrest.insert(
            table = TABLE,
            serializer = PoolDataPointRow.serializer(),
            rows = rows,
            // Nothing reads the rows back. The pool is not client-readable at all, so asking
            // for a representation would be asking for something RLS is going to withhold.
            returnRows = false,
        )
    }

    private suspend fun idsBySlug(): Map<String, String> =
        postgrest.select(table = TABLE_CATEGORIES, serializer = PoolCategoryIdRow.serializer())
            .associate { it.slug to it.id }

    private suspend fun cityIdsByName(): Map<String, String> =
        postgrest.select(table = TABLE_CITIES, serializer = PoolCityIdRow.serializer())
            .associate { it.name.lowercase() to it.id }

    private companion object {
        const val TABLE = "fairness_data_points"
        const val TABLE_CATEGORIES = "service_categories"
        const val TABLE_CITIES = "cities"

        /** Says where the row came from, so the pool can be read back by provenance. */
        const val SOURCE_BILL_CHECK = "bill_check"
    }
}

@Serializable
private data class PoolDataPointRow(
    @SerialName("service_category_id") val serviceCategoryId: String,
    @SerialName("city_id") val cityId: String,
    @SerialName("car_make") val carMake: String?,
    @SerialName("fuel_type") val fuelType: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("workshop_tier") val workshopTier: String?,
    @SerialName("segment") val segment: String?,
    @SerialName("source") val source: String,
)

@Serializable
private data class PoolCategoryIdRow(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String,
)

@Serializable
private data class PoolCityIdRow(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)
