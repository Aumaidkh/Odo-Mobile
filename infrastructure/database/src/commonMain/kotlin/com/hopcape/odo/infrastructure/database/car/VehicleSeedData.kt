package com.hopcape.odo.infrastructure.database.car

import com.hopcape.odo.infrastructure.database.db.OdoDatabase

/** One model and its trim ladder, in ladder order (base first). */
internal data class SeedModel(val name: String, val variants: List<String> = emptyList())

/** A make and its popular models, used to seed the picker reference tables. */
internal data class SeedMake(val name: String, val models: List<SeedModel>)

/**
 * Car makes and models sold in India — current and commonly-owned discontinued ones alike,
 * since Odo tracks cars people already own, not new-car shopping. Order here is the display
 * order, and the first few makes are also what `VehicleCatalog.popularMakes()` returns — so
 * market share is stated once.
 *
 * **This is best-effort reference data, not verified manufacturer data.** Trim ladders change
 * with every facelift and vary by fuel/transmission, so some entries will be stale or wrong.
 * They must be checked against manufacturer sources before launch: a wrong trim doesn't just
 * look sloppy, it feeds per-km cost and the fairness benchmarks that Odo's whole value rests
 * on. Correcting a name here is cheap — bump [CURRENT_SEED_VERSION] and it reaches every
 * installed app on its next launch (see [seedVehicleReferenceData]).
 *
 * Growing this list is now also unnecessary for most future cars: [VehicleCatalogRefresher]
 * pulls fresher makes/models from Supabase in the background, so `RAW_SEED` below only has to
 * cover day-one and offline installs, not every car forever. A missing make or model in the
 * meantime is not a dead end either — both pickers offer "not listed" free text, which is
 * also how new entries reach the shared catalog in the first place.
 *
 * Every model is *also* seeded without a trim (see [seedVehicleReferenceData]), so an owner
 * whose exact variant is missing can still name their car instead of picking a wrong one.
 *
 * ## Editing this list
 *
 * One line per model: `Make | Model | Trim1, Trim2, Trim3`. The trim list is optional — leave
 * it off (or blank) for a model seeded with no trim ladder at all. Lines for the same make
 * don't need to be contiguous, but keeping them together is the whole point of this format:
 * a new model is one line, a new make is a handful of lines, and both are a plain-text diff
 * instead of a nested builder call.
 */
private val RAW_SEED = """
Maruti Suzuki | Swift | LXI, VXI, ZXI, ZXI+
Maruti Suzuki | Baleno | Sigma, Delta, Zeta, Alpha
Maruti Suzuki | Dzire | LXI, VXI, ZXI, ZXI+
Maruti Suzuki | WagonR | LXI, VXI, ZXI, ZXI+
Maruti Suzuki | Brezza | LXI, VXI, ZXI, ZXI+
Maruti Suzuki | Ertiga | LXI, VXI, ZXI, ZXI+
Maruti Suzuki | Alto K10 | STD, LXI, VXI, VXI+
Maruti Suzuki | Fronx | Sigma, Delta, Delta+, Zeta, Alpha
Maruti Suzuki | Grand Vitara | Sigma, Delta, Zeta, Alpha
Maruti Suzuki | Eeco | STD, 5 STR
Maruti Suzuki | Celerio | LXI, VXI, ZXI, ZXI+
Maruti Suzuki | S-Presso | STD, LXI, VXI, VXI+
Hyundai | i20 | Era, Magna, Sportz, Asta, Asta (O)
Hyundai | Venue | E, S, S (O), SX, SX (O)
Hyundai | Creta | E, EX, S, SX, SX (O)
Hyundai | Verna | EX, S, SX, SX (O)
Hyundai | Exter | EX, S, SX, SX (O)
Hyundai | Grand i10 Nios | Era, Magna, Sportz, Asta
Hyundai | Aura | E, S, SX, SX (O)
Hyundai | Alcazar | Prestige, Platinum, Signature
Hyundai | Tucson | Signature, Platinum
Hyundai | Santro | D-Lite, Era, Magna, Sportz, Asta
Tata | Nexon | Smart, Pure, Creative, Fearless
Tata | Punch | Pure, Adventure, Accomplished, Creative
Tata | Tiago | XE, XM, XT, XZ, XZ+
Tata | Altroz | XE, XM+, XT, XZ, XZ+
Tata | Harrier | Smart, Pure, Adventure, Fearless
Tata | Safari | Smart, Pure, Adventure, Accomplished
Tata | Tigor | XE, XM, XZ, XZ+
Tata | Curvv | Creative, Accomplished, Empowered
Mahindra | Scorpio-N | Z2, Z4, Z6, Z8, Z8 L
Mahindra | XUV700 | MX, AX3, AX5, AX7, AX7 L
Mahindra | Thar | AX, AX (O), LX
Mahindra | Bolero | B4, B6, B6 (O)
Mahindra | XUV300 | W2, W4, W6, W8, W8 (O)
Mahindra | Scorpio Classic | S, S11
Mahindra | XUV400 | EC, EL
Mahindra | Bolero Neo | N4, N8, N10
Toyota | Innova Crysta | GX, VX, ZX
Toyota | Innova Hycross | GX, VX, ZX, ZX (O)
Toyota | Fortuner | 4x2, 4x4, Legender
Toyota | Glanza | E, S, G, V
Toyota | Urban Cruiser Hyryder | E, S, G, V
Toyota | Rumion | S, G, V
Toyota | Camry | Hybrid
Toyota | Land Cruiser | VX, ZX
Kia | Seltos | HTE, HTK, HTK+, HTX, GTX+
Kia | Sonet | HTE, HTK, HTK+, HTX, GTX+
Kia | Carens | Premium, Prestige, Luxury, Luxury Plus
Kia | Carnival | Premium, Prestige, Limousine
Kia | EV6 | GT Line
Honda | City | SV, V, VX, ZX
Honda | Amaze | E, S, VX
Honda | Elevate | SV, V, VX, ZX
Honda | Jazz | V, VX
Renault | Kwid | RXE, RXL, RXT, Climber
Renault | Triber | RXE, RXL, RXT, RXZ
Renault | Kiger | RXE, RXL, RXT, RXZ
Volkswagen | Virtus | Comfortline, Highline, Topline, GT Plus
Volkswagen | Taigun | Comfortline, Highline, Topline, GT Plus
Volkswagen | Tiguan | Elegance
Skoda | Slavia | Active, Ambition, Style
Skoda | Kushaq | Active, Ambition, Style
Skoda | Octavia | Style, L&K
Skoda | Superb | Style, L&K
Skoda | Kodiaq | Style, L&K
MG | Hector | Style, Smart, Sharp, Savvy
MG | Astor | Style, Super, Smart, Sharp, Savvy
MG | ZS EV | Excite, Exclusive
MG | Comet EV | Pace, Play, Plush
MG | Windsor EV | Excite, Exclusive
Nissan | Magnite | XE, XL, XV, XV Premium
Jeep | Compass | Sport, Longitude, Limited, Model S
Jeep | Meridian | Longitude, Limited, Overland
Citroen | C3 | Live, Feel, Shine
Citroen | C3 Aircross | You, Plus, Max
Citroen | Basalt | Plus, Max
BYD | Atto 3 | Superior
BYD | Seal | Premium, Performance
BYD | e6 |
Mercedes-Benz | A-Class Limousine | Progressive
Mercedes-Benz | C-Class | Avantgarde, AMG Line
Mercedes-Benz | E-Class | Avantgarde, AMG Line
Mercedes-Benz | GLA | Progressive, AMG Line
Mercedes-Benz | GLC | Avantgarde, AMG Line
Mercedes-Benz | S-Class | S 350d, S 450
BMW | 3 Series | Sport Line, Luxury Line, M Sport
BMW | 5 Series | Sport Line, Luxury Line, M Sport
BMW | X1 | sDrive, xLine, M Sport
BMW | X3 | xLine, M Sport
BMW | X5 | xLine, M Sport
Audi | A4 | Premium, Premium Plus, Technology
Audi | A6 | Premium, Premium Plus, Technology
Audi | Q3 | Premium, Premium Plus, Technology
Audi | Q5 | Premium Plus, Technology
Volvo | XC40 | Momentum, Inscription, Ultimate
Volvo | XC60 | Momentum, Inscription, Ultimate
Volvo | XC90 | Momentum, Inscription, Ultimate
Land Rover | Range Rover | SE, HSE, Autobiography
Land Rover | Range Rover Sport | SE, HSE, Autobiography
Land Rover | Range Rover Evoque | S, SE, HSE
Land Rover | Discovery Sport | S, SE, HSE
Land Rover | Defender | 90, 110, 130
Jaguar | XF | Prestige, Portfolio, R-Dynamic
Jaguar | F-Pace | Prestige, Portfolio, R-Dynamic
Lexus | ES | Elegant, Luxury
Lexus | NX | Elegant, Luxury, F Sport
Lexus | RX | Elegant, Luxury, F Sport
MINI | Cooper | Classic, Favoured
MINI | Countryman | Classic, Favoured, JCW
Porsche | Macan | Base, S, GTS
Porsche | Cayenne | Base, S, GTS
Isuzu | D-Max V-Cross | Base, Z
Isuzu | MU-X | Base, Z
Force Motors | Gurkha | 5-Door
Force Motors | Trax | Cruiser
Mitsubishi | Pajero Sport | Select Plus, Select Plus AT
Fiat | Punto | Active, Dynamic, Emotion
Fiat | Linea | Active, Dynamic, Emotion
Chevrolet | Beat | LS, LT, LTZ
Chevrolet | Spark | LS, LT
Chevrolet | Cruze | LT, LTZ
Chevrolet | Tavera | LS, Neo 3
Datsun | GO | D, A, T
Datsun | redi-GO | D, A, T
Premier | Rio | NX, GX
""".trimIndent()

/**
 * [RAW_SEED], parsed into the shape [seedVehicleReferenceData] inserts. `groupBy` keeps both
 * the first-appearance order of each make and the append order of its models — the same
 * ordering a hand-written `listOf(SeedMake(...))` would have given, so `display_order` still
 * falls straight out of list position.
 */
internal val VEHICLE_SEED: List<SeedMake> by lazy {
    RAW_SEED.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val columns = line.split("|").map { it.trim() }
            val make = columns[0]
            val model = SeedModel(
                name = columns[1],
                variants = columns.getOrNull(2)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty(),
            )
            make to model
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .map { (make, models) -> SeedMake(make, models) }
}

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
 * Bump whenever [RAW_SEED] changes — a bigger catalog, a corrected trim, anything. This is
 * the whole mechanism that lets a data fix ship without a schema migration: on a version
 * mismatch, [seedVehicleReferenceData] replaces every row in `vehicle_make`/`vehicle_model`
 * with what [VEHICLE_SEED] says now.
 */
private const val CURRENT_SEED_VERSION = 1L

/**
 * Populate the picker reference tables from [VEHICLE_SEED], replacing whatever they held
 * before. A no-op once the stored `seed_version` already matches [CURRENT_SEED_VERSION] — an
 * ordinary app launch does not re-run hundreds of inserts every time.
 *
 * This used to be "insert once, forever" (skip if the table already had rows), which meant a
 * bigger or corrected catalog could only reach an installed app via a reinstall. Comparing a
 * stored version instead means shipping more cars later is "edit [RAW_SEED], bump
 * [CURRENT_SEED_VERSION]" — no schema migration needed per catalog update, only when the
 * `vehicle_catalog_meta` table itself changes shape.
 *
 * Full delete-and-reinsert rather than INSERT-OR-IGNORE-on-top is what lets this also fix a
 * typo, not just add rows: `cars.make`/`cars.model` are plain strings with no foreign key
 * into these tables (DB_SCHEMA), so replacing every reference row can never orphan or change
 * a car someone has already saved.
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
    val metaQueries = database.vehicleCatalogMetaQueries
    val storedVersion = metaQueries.selectSeedVersion().executeAsOneOrNull()
    if (storedVersion == CURRENT_SEED_VERSION) return

    val makeQueries = database.vehicleMakeQueries
    val modelQueries = database.vehicleModelQueries

    database.transaction {
        modelQueries.deleteAllModels()
        makeQueries.deleteAllMakes()

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

        metaQueries.setSeedVersion(CURRENT_SEED_VERSION)
    }
}

/** Ordering slots reserved per model — one for the trim-less row plus its trims. */
private const val VARIANT_ORDER_SPAN = 100L
