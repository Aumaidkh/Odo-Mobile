package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.infrastructure.database.db.OdoDatabase

/** One model and its trim ladder, in ladder order (base first). */
internal data class SeedModel(val name: String, val variants: List<String> = emptyList())

/** A make and its popular models, used to seed the picker reference tables. */
internal data class SeedMake(val name: String, val models: List<SeedModel>)

/**
 * Top Indian car brands (roughly by market share) with their popular models and trims.
 * Order here is the display order, and the first few makes are also what
 * `VehicleCatalog.popularMakes()` returns — so market share is stated once.
 *
 * **These trims are best-effort reference data, not verified manufacturer data.** Trim
 * ladders change with every facelift and vary by fuel/transmission, so some entries will be
 * stale or wrong. They must be checked against manufacturer sources before launch: a wrong
 * trim doesn't just look sloppy, it feeds per-km cost and the fairness benchmarks that Odo's
 * whole value rests on. Correcting them is cheap — seeding is idempotent (INSERT OR IGNORE
 * on deterministic slugs), so fixing a name and bumping the DB version is the whole job.
 *
 * Every model is *also* seeded without a trim (see [seedVehicleReferenceData]), so an owner
 * whose exact variant is missing can still name their car instead of picking a wrong one.
 */
internal val VEHICLE_SEED: List<SeedMake> = listOf(
    SeedMake(
        "Maruti Suzuki",
        listOf(
            SeedModel("Swift", listOf("LXI", "VXI", "ZXI", "ZXI+")),
            SeedModel("Baleno", listOf("Sigma", "Delta", "Zeta", "Alpha")),
            SeedModel("Dzire", listOf("LXI", "VXI", "ZXI", "ZXI+")),
            SeedModel("WagonR", listOf("LXI", "VXI", "ZXI", "ZXI+")),
            SeedModel("Brezza", listOf("LXI", "VXI", "ZXI", "ZXI+")),
            SeedModel("Ertiga", listOf("LXI", "VXI", "ZXI", "ZXI+")),
            SeedModel("Alto K10", listOf("STD", "LXI", "VXI", "VXI+")),
            SeedModel("Fronx", listOf("Sigma", "Delta", "Delta+", "Zeta", "Alpha")),
            SeedModel("Grand Vitara", listOf("Sigma", "Delta", "Zeta", "Alpha")),
        ),
    ),
    SeedMake(
        "Hyundai",
        listOf(
            SeedModel("i20", listOf("Era", "Magna", "Sportz", "Asta", "Asta (O)")),
            SeedModel("Venue", listOf("E", "S", "S (O)", "SX", "SX (O)")),
            SeedModel("Creta", listOf("E", "EX", "S", "SX", "SX (O)")),
            SeedModel("Verna", listOf("EX", "S", "SX", "SX (O)")),
            SeedModel("Exter", listOf("EX", "S", "SX", "SX (O)")),
            SeedModel("Grand i10 Nios", listOf("Era", "Magna", "Sportz", "Asta")),
            SeedModel("Aura", listOf("E", "S", "SX", "SX (O)")),
        ),
    ),
    SeedMake(
        "Tata",
        listOf(
            SeedModel("Nexon", listOf("Smart", "Pure", "Creative", "Fearless")),
            SeedModel("Punch", listOf("Pure", "Adventure", "Accomplished", "Creative")),
            SeedModel("Tiago", listOf("XE", "XM", "XT", "XZ", "XZ+")),
            SeedModel("Altroz", listOf("XE", "XM+", "XT", "XZ", "XZ+")),
            SeedModel("Harrier", listOf("Smart", "Pure", "Adventure", "Fearless")),
            SeedModel("Safari", listOf("Smart", "Pure", "Adventure", "Accomplished")),
            SeedModel("Tigor", listOf("XE", "XM", "XZ", "XZ+")),
        ),
    ),
    SeedMake(
        "Mahindra",
        listOf(
            SeedModel("Scorpio-N", listOf("Z2", "Z4", "Z6", "Z8", "Z8 L")),
            SeedModel("XUV700", listOf("MX", "AX3", "AX5", "AX7", "AX7 L")),
            SeedModel("Thar", listOf("AX", "AX (O)", "LX")),
            SeedModel("Bolero", listOf("B4", "B6", "B6 (O)")),
            SeedModel("XUV300", listOf("W2", "W4", "W6", "W8", "W8 (O)")),
            SeedModel("Scorpio Classic", listOf("S", "S11")),
        ),
    ),
    SeedMake(
        "Toyota",
        listOf(
            SeedModel("Innova Crysta", listOf("GX", "VX", "ZX")),
            SeedModel("Innova Hycross", listOf("GX", "VX", "ZX", "ZX (O)")),
            SeedModel("Fortuner", listOf("4x2", "4x4", "Legender")),
            SeedModel("Glanza", listOf("E", "S", "G", "V")),
            SeedModel("Urban Cruiser Hyryder", listOf("E", "S", "G", "V")),
            SeedModel("Rumion", listOf("S", "G", "V")),
        ),
    ),
    SeedMake(
        "Kia",
        listOf(
            SeedModel("Seltos", listOf("HTE", "HTK", "HTK+", "HTX", "GTX+")),
            SeedModel("Sonet", listOf("HTE", "HTK", "HTK+", "HTX", "GTX+")),
            SeedModel("Carens", listOf("Premium", "Prestige", "Luxury", "Luxury Plus")),
            SeedModel("Carnival", listOf("Premium", "Prestige", "Limousine")),
        ),
    ),
    SeedMake(
        "Honda",
        listOf(
            SeedModel("City", listOf("SV", "V", "VX", "ZX")),
            SeedModel("Amaze", listOf("E", "S", "VX")),
            SeedModel("Elevate", listOf("SV", "V", "VX", "ZX")),
        ),
    ),
    SeedMake(
        "Renault",
        listOf(
            SeedModel("Kwid", listOf("RXE", "RXL", "RXT", "Climber")),
            SeedModel("Triber", listOf("RXE", "RXL", "RXT", "RXZ")),
            SeedModel("Kiger", listOf("RXE", "RXL", "RXT", "RXZ")),
        ),
    ),
    SeedMake(
        "Volkswagen",
        listOf(
            SeedModel("Virtus", listOf("Comfortline", "Highline", "Topline", "GT Plus")),
            SeedModel("Taigun", listOf("Comfortline", "Highline", "Topline", "GT Plus")),
        ),
    ),
    SeedMake(
        "Skoda",
        listOf(
            SeedModel("Slavia", listOf("Active", "Ambition", "Style")),
            SeedModel("Kushaq", listOf("Active", "Ambition", "Style")),
        ),
    ),
    SeedMake(
        "MG",
        listOf(
            SeedModel("Hector", listOf("Style", "Smart", "Sharp", "Savvy")),
            SeedModel("Astor", listOf("Style", "Super", "Smart", "Sharp", "Savvy")),
            SeedModel("ZS EV", listOf("Excite", "Exclusive")),
            SeedModel("Comet EV", listOf("Pace", "Play", "Plush")),
        ),
    ),
    SeedMake(
        "Nissan",
        listOf(
            SeedModel("Magnite", listOf("XE", "XL", "XV", "XV Premium")),
        ),
    ),
)

/**
 * Deterministic, INSERT-OR-IGNORE-friendly id slug.
 *
 * `+` is spelled out rather than stripped, because Indian trim ladders distinguish trims by
 * exactly that character — "ZXI" and "ZXI+" are different cars. Dropping it collapsed them
 * to one slug, and INSERT OR IGNORE then silently discarded the second, losing the top trim
 * of most models. `seed_producesARowForEveryModelAndTrim` guards against any future
 * collision of this kind.
 */
private fun slug(vararg parts: String): String = parts.joinToString("-") { part ->
    part.lowercase().replace("+", " plus ").replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

/**
 * Populate the picker reference tables once. Idempotent: a no-op when makes
 * already exist, and INSERT OR IGNORE on stable slugs guards re-runs.
 *
 * Each model is inserted twice over: once with no trim, then once per trim. The trim-less
 * row is what stops a missing or renamed variant from blocking someone — they can always
 * pick just "Swift".
 *
 * `display_order` packs model and trim position into one number
 * (`modelIndex * VARIANT_ORDER_SPAN + variantIndex`), which keeps a model's trims together
 * and in ladder order without a second sort column. The span caps a model at
 * [VARIANT_ORDER_SPAN] − 1 trims, far above any real ladder.
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
                val baseOrder = modelIndex.toLong() * VARIANT_ORDER_SPAN
                // The trim-less entry, always first within its model.
                modelQueries.insertModel(
                    id = slug("model", make.name, model.name),
                    make_id = makeId,
                    name = model.name,
                    variant = null,
                    display_order = baseOrder,
                )
                model.variants.forEachIndexed { variantIndex, variant ->
                    modelQueries.insertModel(
                        id = slug("model", make.name, model.name, variant),
                        make_id = makeId,
                        name = model.name,
                        variant = variant,
                        display_order = baseOrder + variantIndex + 1,
                    )
                }
            }
        }
    }
}

/** Ordering slots reserved per model — one for the trim-less row plus its trims. */
private const val VARIANT_ORDER_SPAN = 100L
