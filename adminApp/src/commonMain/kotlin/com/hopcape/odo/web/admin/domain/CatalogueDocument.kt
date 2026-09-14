package com.hopcape.odo.web.admin.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The catalogs as a file, so one environment can be poured into another.
 *
 * Dev is where a catalog is actually built — somebody adds forty trims, fixes a
 * benchmark, retires an item — and production is where it has to end up. Retyping
 * it is how the two drift.
 *
 * The document names what it holds. Importing a vehicle file into the service
 * catalog is a plausible slip, and without the [kind] the panel would send it and
 * let PostgREST answer with a column error nobody can act on.
 *
 * Service items travel **without their id**: it is a `gen_random_uuid()`, so the
 * same item is a different id in each project. `slug` is the name both agree on.
 * Vehicles travel **with** theirs, for the opposite reason — their ids are slugs
 * the database builds from the name, so they already match.
 */
object CatalogueDocument {

    const val SERVICE_KIND: String = "odo.service-catalogue"
    const val VEHICLE_KIND: String = "odo.vehicle-catalogue"
    const val MIME: String = "application/json"
    const val ACCEPT: String = ".json,application/json"

    /** What a read produced: the rows, or why there are none. */
    sealed interface Read<out T> {
        data class Rows<T>(val value: T) : Read<T>
        /** The file parsed but is not this catalog, or did not parse at all. */
        data object WrongFile : Read<Nothing>
    }

    fun writeServiceItems(items: List<ServiceItem>): String =
        JSON.encodeToString(
            ServiceFile.serializer(),
            ServiceFile(items = items.map(ServiceItem::toRow)),
        )

    fun readServiceItems(document: String): Read<List<ServiceItem>> {
        val file = decode(ServiceFile.serializer(), document) ?: return Read.WrongFile
        if (file.kind != SERVICE_KIND) return Read.WrongFile
        return Read.Rows(file.items.map(ServiceRow::toItem))
    }

    fun writeVehicles(makes: List<VehicleMake>, models: List<VehicleModel>): String =
        JSON.encodeToString(
            VehicleFile.serializer(),
            VehicleFile(
                makes = makes.map { MakeRow(it.id, it.name, it.displayOrder) },
                models = models.map { ModelRow(it.id, it.makeId, it.name, it.variant, it.displayOrder) },
            ),
        )

    fun readVehicles(document: String): Read<VehicleCatalogue> {
        val file = decode(VehicleFile.serializer(), document) ?: return Read.WrongFile
        if (file.kind != VEHICLE_KIND) return Read.WrongFile
        return Read.Rows(
            VehicleCatalogue(
                makes = file.makes.map { VehicleMake(it.id, it.name, it.displayOrder) },
                models = file.models.map { VehicleModel(it.id, it.makeId, it.name, it.variant, it.displayOrder) },
            ),
        )
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, document: String): T? =
        runCatching { JSON.decodeFromString(serializer, document) }.getOrNull()

    /**
     * Lenient on read so a file written by a later version still imports what this
     * version understands, and explicit on write so a cleared field travels as null
     * rather than as an absent key the importer would read as "leave it alone".
     */
    private val JSON = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = true
        encodeDefaults = true
    }
}

/** Both vehicle tables, which only ever travel together — a model needs its make. */
data class VehicleCatalogue(
    val makes: List<VehicleMake>,
    val models: List<VehicleModel>,
)

@Serializable
private data class ServiceFile(
    val kind: String = CatalogueDocument.SERVICE_KIND,
    val version: Int = 1,
    val items: List<ServiceRow>,
)

@Serializable
private data class VehicleFile(
    val kind: String = CatalogueDocument.VEHICLE_KIND,
    val version: Int = 1,
    val makes: List<MakeRow>,
    val models: List<ModelRow>,
)

/** Column names, not field names, so the file reads like the table it came from. */
@Serializable
private data class ServiceRow(
    val slug: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("applies_to") val appliesTo: List<String>? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("interval_km") val intervalKm: Int? = null,
    @SerialName("interval_months") val intervalMonths: Int? = null,
    @SerialName("benchmark_paise") val benchmarkPaise: Long? = null,
    val notes: String? = null,
)

@Serializable
private data class MakeRow(
    val id: String,
    val name: String,
    @SerialName("display_order") val displayOrder: Long,
)

@Serializable
private data class ModelRow(
    val id: String,
    @SerialName("make_id") val makeId: String,
    val name: String,
    val variant: String? = null,
    @SerialName("display_order") val displayOrder: Long,
)

private fun ServiceItem.toRow() = ServiceRow(
    slug = slug,
    displayName = name,
    appliesTo = appliesTo,
    isActive = isActive,
    intervalKm = intervalKm,
    intervalMonths = intervalMonths,
    benchmarkPaise = benchmarkPaise,
    notes = notes,
)

/**
 * The id is blank on the way in. Nothing downstream reads it — the import keys on
 * the slug — and inventing one here would put a dev uuid into production.
 */
private fun ServiceRow.toItem() = ServiceItem(
    id = "",
    slug = slug,
    name = displayName,
    intervalKm = intervalKm,
    intervalMonths = intervalMonths,
    benchmarkPaise = benchmarkPaise,
    notes = notes,
    isActive = isActive,
    appliesTo = appliesTo,
)
