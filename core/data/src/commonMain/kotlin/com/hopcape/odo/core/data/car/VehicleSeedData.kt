package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.data.db.OdoDatabase

/** A make and its popular models, used to seed the dropdown reference tables. */
internal data class SeedMake(val name: String, val models: List<String>)

/**
 * Top Indian car brands (roughly by market share) with a handful of popular
 * models each. Order here is the dropdown display order. Extend freely — seeding
 * is idempotent (INSERT OR IGNORE keyed on deterministic slugs).
 */
internal val VEHICLE_SEED: List<SeedMake> = listOf(
    SeedMake("Maruti Suzuki", listOf("Swift", "Baleno", "Dzire", "WagonR", "Brezza", "Ertiga", "Alto K10", "Fronx", "Grand Vitara")),
    SeedMake("Hyundai", listOf("i20", "Venue", "Creta", "Verna", "Exter", "Grand i10 Nios", "Aura")),
    SeedMake("Tata", listOf("Nexon", "Punch", "Tiago", "Altroz", "Harrier", "Safari", "Tigor")),
    SeedMake("Mahindra", listOf("Scorpio-N", "XUV700", "Thar", "Bolero", "XUV300", "Scorpio Classic")),
    SeedMake("Toyota", listOf("Innova Crysta", "Innova Hycross", "Fortuner", "Glanza", "Urban Cruiser Hyryder", "Rumion")),
    SeedMake("Kia", listOf("Seltos", "Sonet", "Carens", "Carnival")),
    SeedMake("Honda", listOf("City", "Amaze", "Elevate")),
    SeedMake("Renault", listOf("Kwid", "Triber", "Kiger")),
    SeedMake("Volkswagen", listOf("Virtus", "Taigun")),
    SeedMake("Skoda", listOf("Slavia", "Kushaq")),
    SeedMake("MG", listOf("Hector", "Astor", "ZS EV", "Comet EV")),
    SeedMake("Nissan", listOf("Magnite")),
)

/** Deterministic, INSERT-OR-IGNORE-friendly id slug. */
private fun slug(vararg parts: String): String =
    parts.joinToString("-") { it.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') }

/**
 * Populate the dropdown reference tables once. Idempotent: a no-op when makes
 * already exist, and INSERT OR IGNORE on stable slugs guards re-runs.
 */
internal fun seedVehicleReferenceData(database: OdoDatabase) {
    val makeQueries = database.vehicleMakeQueries
    val modelQueries = database.vehicleModelQueries
    if (makeQueries.countMakes().executeAsOne() > 0L) return

    database.transaction {
        VEHICLE_SEED.forEachIndexed { makeIndex, make ->
            val makeId = slug("make", make.name)
            makeQueries.insertMake(
                id = makeId,
                name = make.name,
                display_order = makeIndex.toLong(),
            )
            make.models.forEachIndexed { modelIndex, model ->
                modelQueries.insertModel(
                    id = slug("model", make.name, model),
                    make_id = makeId,
                    name = model,
                    display_order = modelIndex.toLong(),
                )
            }
        }
    }
}
